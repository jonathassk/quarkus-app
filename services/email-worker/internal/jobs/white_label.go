package jobs

import (
	"context"
	"fmt"
	"html"
	"strings"
)

type WhiteLabelEmailInput struct {
	ToEmail      string `json:"toEmail"`
	Subject      string `json:"subject"`
	TextBody     string `json:"textBody"`
	HTMLBody     string `json:"htmlBody"`
	AgencyID     string `json:"agencyId"`
	TemplateKind string `json:"templateKind"` // proposal_sent | boarding_reminder | post_trip
	TripName     string `json:"tripName"`
	ShareURL     string `json:"shareUrl"`
}

type agencyBrand struct {
	Name          string
	LogoURL       string
	PrimaryColor  string
	WhatsApp      string
}

// SendWhiteLabel envia e-mail com header rebrandeado (logo/cor da agência).
// From continua sendo a identidade SES do Baggagi; o HTML carrega a marca da agência.
func (r *Runner) SendWhiteLabel(ctx context.Context, in WhiteLabelEmailInput) (Result, error) {
	res := Result{Action: "send_white_label", SESReady: r.Mailer.Ready()}

	to := strings.TrimSpace(in.ToEmail)
	if to == "" || !strings.Contains(to, "@") {
		return res, fmt.Errorf("toEmail is required")
	}

	brand, err := r.loadAgencyBrand(ctx, strings.TrimSpace(in.AgencyID))
	if err != nil {
		return res, err
	}

	subject := strings.TrimSpace(in.Subject)
	if subject == "" {
		subject = defaultSubject(in.TemplateKind, brand.Name, in.TripName)
	}

	innerHTML := strings.TrimSpace(in.HTMLBody)
	if innerHTML == "" {
		innerHTML = defaultBodyHTML(in.TemplateKind, brand, in.TripName, in.ShareURL)
	}

	htmlBody := wrapAgencyTemplate(brand, innerHTML)
	text := strings.TrimSpace(in.TextBody)
	if text == "" {
		text = stripTags(htmlBody)
	}

	if !r.Mailer.Ready() {
		r.Log.Info("ses disabled — would send white-label", "to", to, "agency", in.AgencyID, "subject", subject)
		res.Skipped++
		res.Message = "SES disabled"
		return res, nil
	}

	msgID, err := r.Mailer.Send(ctx, to, subject, text, htmlBody)
	if err != nil {
		r.Log.Error("ses send white-label", "to", to, "err", err)
		res.Failed++
		return res, err
	}
	if msgID == "" {
		res.Failed++
		return res, fmt.Errorf("empty SES message id")
	}

	res.Sent++
	res.Message = "sent"
	return res, nil
}

func (r *Runner) loadAgencyBrand(ctx context.Context, agencyID string) (agencyBrand, error) {
	brand := agencyBrand{
		Name:         "Sua Agência",
		PrimaryColor: "#000000",
	}
	if agencyID == "" {
		return brand, nil
	}
	row := r.DB.QueryRow(ctx, `
		SELECT COALESCE(name, ''), COALESCE(logo_url, ''), COALESCE(primary_color, '#000000'),
		       COALESCE(whatsapp_number, '')
		FROM agencies WHERE id = $1::uuid`, agencyID)
	var name, logo, color, wa string
	if err := row.Scan(&name, &logo, &color, &wa); err != nil {
		r.Log.Warn("agency brand lookup failed — using defaults", "agencyId", agencyID, "err", err)
		return brand, nil
	}
	if strings.TrimSpace(name) != "" {
		brand.Name = name
	}
	brand.LogoURL = logo
	if strings.TrimSpace(color) != "" {
		brand.PrimaryColor = color
	}
	brand.WhatsApp = wa
	return brand, nil
}

func wrapAgencyTemplate(brand agencyBrand, innerHTML string) string {
	color := html.EscapeString(brand.PrimaryColor)
	name := html.EscapeString(brand.Name)
	logoBlock := ""
	if strings.TrimSpace(brand.LogoURL) != "" {
		logoBlock = fmt.Sprintf(
			`<img src="%s" alt="%s" style="max-height:48px;max-width:180px;display:block;margin:0 auto 12px;" />`,
			html.EscapeString(brand.LogoURL), name)
	}
	return fmt.Sprintf(`<!DOCTYPE html>
<html><head><meta charset="utf-8"/></head>
<body style="margin:0;padding:0;background:#f5f5f5;font-family:system-ui,-apple-system,sans-serif;">
  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f5;padding:24px 12px;">
    <tr><td align="center">
      <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;">
        <tr><td style="background:%s;padding:20px 24px;text-align:center;color:#fff;">
          %s
          <div style="font-size:18px;font-weight:600;">%s</div>
        </td></tr>
        <tr><td style="padding:24px;color:#222;font-size:15px;line-height:1.5;">%s</td></tr>
        <tr><td style="padding:12px 24px 24px;color:#888;font-size:12px;text-align:center;">
          Enviado com tecnologia Baggagi
        </td></tr>
      </table>
    </td></tr>
  </table>
</body></html>`, color, logoBlock, name, innerHTML)
}

func defaultSubject(kind, agencyName, tripName string) string {
	switch kind {
	case "boarding_reminder":
		return fmt.Sprintf("%s — lembrete de embarque: %s", agencyName, tripName)
	case "post_trip":
		return fmt.Sprintf("%s — como foi sua viagem?", agencyName)
	default:
		if tripName != "" {
			return fmt.Sprintf("%s — proposta: %s", agencyName, tripName)
		}
		return fmt.Sprintf("%s — sua proposta de viagem", agencyName)
	}
}

func defaultBodyHTML(kind string, brand agencyBrand, tripName, shareURL string) string {
	safeTrip := html.EscapeString(tripName)
	safeURL := html.EscapeString(shareURL)
	switch kind {
	case "boarding_reminder":
		return fmt.Sprintf(`<p>Olá!</p><p>Faltam 7 dias para o embarque da viagem <strong>%s</strong>.</p>
<p>Qualquer dúvida, fale conosco%s.</p>`, safeTrip, whatsappHint(brand))
	case "post_trip":
		return fmt.Sprintf(`<p>Esperamos que tenha aproveitado a viagem <strong>%s</strong>!</p>
<p>Conte como foi a experiência — sua opinião é muito importante.</p>`, safeTrip)
	default:
		cta := ""
		if shareURL != "" {
			cta = fmt.Sprintf(`<p style="margin:20px 0;"><a href="%s" style="background:%s;color:#fff;padding:12px 20px;border-radius:6px;text-decoration:none;display:inline-block;">Ver proposta</a></p>`,
				safeURL, html.EscapeString(brand.PrimaryColor))
		}
		return fmt.Sprintf(`<p>Olá!</p><p>Sua proposta de viagem <strong>%s</strong> está pronta.</p>%s
<p>Estamos à disposição%s.</p>`, safeTrip, cta, whatsappHint(brand))
	}
}

func whatsappHint(brand agencyBrand) string {
	if strings.TrimSpace(brand.WhatsApp) == "" {
		return ""
	}
	return fmt.Sprintf(` pelo WhatsApp <a href="https://wa.me/%s">%s</a>`,
		html.EscapeString(strings.TrimPrefix(brand.WhatsApp, "+")),
		html.EscapeString(brand.WhatsApp))
}
