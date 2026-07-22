package jobs

import (
	"context"
	"fmt"
	"strings"
)

type SendDirectInput struct {
	ToEmail  string `json:"toEmail"`
	Subject  string `json:"subject"`
	TextBody string `json:"textBody"`
	HTMLBody string `json:"htmlBody"`
}

// SendDirect envia um e-mail pontual (ex.: resumo de roteiro) via SES.
func (r *Runner) SendDirect(ctx context.Context, in SendDirectInput) (Result, error) {
	res := Result{Action: "send_direct", SESReady: r.Mailer.Ready()}

	to := strings.TrimSpace(in.ToEmail)
	if to == "" || !strings.Contains(to, "@") {
		return res, fmt.Errorf("toEmail is required")
	}
	if strings.TrimSpace(in.Subject) == "" {
		return res, fmt.Errorf("subject is required")
	}

	text := in.TextBody
	htmlBody := in.HTMLBody
	if strings.TrimSpace(text) == "" && strings.TrimSpace(htmlBody) == "" {
		return res, fmt.Errorf("textBody or htmlBody is required")
	}
	if strings.TrimSpace(text) == "" {
		text = stripTags(htmlBody)
	}

	if !r.Mailer.Ready() {
		r.Log.Info("ses disabled — would send direct", "to", to, "subject", in.Subject)
		res.Skipped++
		res.Message = "SES disabled"
		return res, nil
	}

	msgID, err := r.Mailer.Send(ctx, to, in.Subject, text, htmlBody)
	if err != nil {
		r.Log.Error("ses send direct", "to", to, "err", err)
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
