package jobs

import (
	"context"
	"fmt"
	"html"
	"time"

	"github.com/baggagi/email-worker/internal/db"
)

const TypeDocumentExpiryPrefix = "DOCUMENT_EXPIRY_"

// Janelas de aviso antes do vencimento (dias). A primeira reflete o texto do
// front ("aviso 6 meses antes"); as demais reforçam o lembrete conforme a
// data se aproxima.
var documentExpiryWindowDays = []int32{180, 90, 30, 7, 1}

// DocumentExpiryReminders: e-mails para documentos (passaporte/visto/CNH/extras)
// cujo expiry_date cai exatamente em uma das janelas de aviso, a partir de hoje (UTC).
func (r *Runner) DocumentExpiryReminders(ctx context.Context) (Result, error) {
	res := Result{Action: "document_expiry_reminders", SESReady: r.Mailer.Ready()}
	today := time.Now().UTC().Truncate(24 * time.Hour)

	docs, err := r.DB.ListExpiringDocuments(ctx, today, documentExpiryWindowDays)
	if err != nil {
		return res, fmt.Errorf("list expiring documents: %w", err)
	}

	for _, doc := range docs {
		daysLeft := int(doc.ExpiryDate.Sub(today).Hours() / 24)
		emailType := fmt.Sprintf("%s%dD", TypeDocumentExpiryPrefix, daysLeft)

		already, err := r.DB.AlreadySent(ctx, doc.UserID, emailType, &doc.ID)
		if err != nil {
			r.Log.Error("already-sent check", "err", err)
			res.Failed++
			continue
		}
		if already {
			res.Skipped++
			continue
		}

		docName := documentDisplayName(doc)
		subject := fmt.Sprintf("%s vence em %d dias", docName, daysLeft)
		text, htmlBody := documentExpiryBodies(doc, docName, daysLeft, r.CFG.AppPublicURL)

		msgID, err := r.Mailer.Send(ctx, doc.Email, subject, text, htmlBody)
		if err != nil {
			r.Log.Error("ses send", "to", doc.Email, "err", err)
			res.Failed++
			continue
		}
		if msgID == "" && !r.Mailer.Ready() {
			r.Log.Info("ses disabled — would send", "to", doc.Email, "document", doc.ID)
			res.Skipped++
			continue
		}
		if msgID == "" {
			res.Failed++
			continue
		}
		if err := r.DB.RecordSent(ctx, doc.UserID, emailType, &doc.ID, msgID); err != nil {
			r.Log.Error("record sent", "err", err)
		}
		res.Sent++
	}

	res.Message = fmt.Sprintf("windows(days)=%v", documentExpiryWindowDays)
	return res, nil
}

func documentDisplayName(doc db.ExpiringDocument) string {
	switch doc.Kind {
	case "PASSPORT":
		return "Passaporte"
	case "VISA":
		return "Visto"
	case "INTERNATIONAL_LICENSE":
		return "CNH Internacional"
	default:
		if doc.Name != "" {
			return doc.Name
		}
		return "Documento"
	}
}

func documentExpiryBodies(doc db.ExpiringDocument, docName string, daysLeft int, appURL string) (string, string) {
	name := doc.FullName
	if name == "" {
		name = "viajante"
	}
	dateStr := doc.ExpiryDate.Format("02/01/2006")
	link := appURL + "/settings"

	text := fmt.Sprintf(
		"Olá, %s!\n\nSeu documento \"%s\" vence em %d dias (%s).\n\nAtualize a data ou renove o documento assim que possível.\n\nAbrir configurações no Baggagi: %s\n\nEquipe Baggagi\n",
		name, docName, daysLeft, dateStr, link,
	)
	htmlBody := fmt.Sprintf(
		`<p>Olá, %s!</p><p>Seu documento <strong>%s</strong> vence em <strong>%d dias</strong> (%s).</p><p>Atualize a data ou renove o documento assim que possível.</p><p><a href="%s">Abrir configurações no Baggagi</a></p><p>Equipe Baggagi</p>`,
		html.EscapeString(name),
		html.EscapeString(docName),
		daysLeft,
		html.EscapeString(dateStr),
		html.EscapeString(link),
	)
	return text, htmlBody
}
