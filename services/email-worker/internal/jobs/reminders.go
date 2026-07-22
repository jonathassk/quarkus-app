package jobs

import (
	"context"
	"fmt"
	"html"
	"log/slog"
	"strings"
	"time"

	"github.com/baggagi/email-worker/internal/config"
	"github.com/baggagi/email-worker/internal/db"
	"github.com/baggagi/email-worker/internal/mail"
	"github.com/google/uuid"
)

const TypeTripReminder12H = "TRIP_REMINDER_12H"
const TypeProductUpdate = "PRODUCT_UPDATE"

type Result struct {
	Action    string `json:"action"`
	Sent      int    `json:"sent"`
	Skipped   int    `json:"skipped"`
	Failed    int    `json:"failed"`
	Trips     int    `json:"trips,omitempty"`
	SESReady  bool   `json:"sesReady"`
	Message   string `json:"message,omitempty"`
	Broadcast string `json:"broadcastId,omitempty"`
}

type Runner struct {
	CFG    config.Config
	DB     *db.Pool
	Mailer *mail.Sender
	Log    *slog.Logger
}

// TripReminders: e-mails quando faltam < ReminderHours para o início do dia da viagem
// (start_date 00:00 no timezone do user; no futuro: horário do voo).
func (r *Runner) TripReminders(ctx context.Context) (Result, error) {
	res := Result{Action: "trip_reminders", SESReady: r.Mailer.Ready()}
	now := time.Now().UTC()

	trips, err := r.DB.ListTripsNearStart(ctx, now)
	if err != nil {
		return res, fmt.Errorf("list trips: %w", err)
	}
	res.Trips = len(trips)

	window := time.Duration(r.CFG.ReminderHours) * time.Hour

	for _, trip := range trips {
		recipients, err := r.DB.ListTripReminderRecipients(ctx, trip.TripID)
		if err != nil {
			r.Log.Error("list recipients", "trip", trip.TripID, "err", err)
			res.Failed++
			continue
		}

		for _, user := range recipients {
			startAt := tripStartAt(trip.StartDate, user.Timezone)
			// Janela: [startAt - 12h, startAt)
			if now.Before(startAt.Add(-window)) || !now.Before(startAt) {
				res.Skipped++
				continue
			}

			ref := trip.TripID
			already, err := r.DB.AlreadySent(ctx, user.UserID, TypeTripReminder12H, &ref)
			if err != nil {
				r.Log.Error("already-sent check", "err", err)
				res.Failed++
				continue
			}
			if already {
				res.Skipped++
				continue
			}

			subject := fmt.Sprintf("Lembrete: %s começa em breve", trip.TripName)
			text, htmlBody := tripReminderBodies(user, trip, r.CFG.AppPublicURL, r.CFG.ReminderHours, startAt)

			msgID, err := r.Mailer.Send(ctx, user.Email, subject, text, htmlBody)
			if err != nil {
				r.Log.Error("ses send", "to", user.Email, "err", err)
				res.Failed++
				continue
			}
			if msgID == "" && !r.Mailer.Ready() {
				r.Log.Info("ses disabled — would send", "to", user.Email, "trip", trip.TripID)
				res.Skipped++
				continue
			}
			if msgID == "" {
				res.Failed++
				continue
			}
			if err := r.DB.RecordSent(ctx, user.UserID, TypeTripReminder12H, &ref, msgID); err != nil {
				r.Log.Error("record sent", "err", err)
			}
			res.Sent++
		}
	}

	res.Message = fmt.Sprintf("window=<%dh until trip-day start", r.CFG.ReminderHours)
	return res, nil
}

type ProductUpdateInput struct {
	Subject     string `json:"subject"`
	TextBody    string `json:"textBody"`
	HTMLBody    string `json:"htmlBody"`
	BroadcastID string `json:"broadcastId"`
	DryRun      bool   `json:"dryRun"`
}

func (r *Runner) ProductUpdate(ctx context.Context, in ProductUpdateInput) (Result, error) {
	res := Result{Action: "product_update", SESReady: r.Mailer.Ready()}
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

	broadcastID, err := parseOrNewUUID(in.BroadcastID)
	if err != nil {
		return res, err
	}
	res.Broadcast = broadcastID.String()

	recipients, err := r.DB.ListProductUpdateRecipients(ctx)
	if err != nil {
		return res, err
	}

	for _, user := range recipients {
		already, err := r.DB.AlreadySent(ctx, user.UserID, TypeProductUpdate, &broadcastID)
		if err != nil {
			res.Failed++
			continue
		}
		if already {
			res.Skipped++
			continue
		}
		if in.DryRun {
			res.Skipped++
			continue
		}

		msgID, err := r.Mailer.Send(ctx, user.Email, in.Subject, text, htmlBody)
		if err != nil {
			r.Log.Error("ses send", "to", user.Email, "err", err)
			res.Failed++
			continue
		}
		if msgID == "" && !r.Mailer.Ready() {
			res.Skipped++
			continue
		}
		if msgID == "" {
			res.Failed++
			continue
		}
		if err := r.DB.RecordSent(ctx, user.UserID, TypeProductUpdate, &broadcastID, msgID); err != nil {
			r.Log.Error("record sent", "err", err)
		}
		res.Sent++
	}

	if in.DryRun {
		res.Message = "dryRun"
	}
	return res, nil
}

// tripStartAt: início do dia da viagem no TZ do usuário (fallback UTC).
// Quando houver horário de voo, substituir este cálculo pela datetime do voo.
func tripStartAt(startDate time.Time, tzName string) time.Time {
	loc := time.UTC
	if strings.TrimSpace(tzName) != "" {
		if l, err := time.LoadLocation(tzName); err == nil {
			loc = l
		}
	}
	y, m, d := startDate.In(time.UTC).Date()
	return time.Date(y, m, d, 0, 0, 0, 0, loc)
}

func tripReminderBodies(user db.Recipient, trip db.TripCandidate, appURL string, hours int, startAt time.Time) (string, string) {
	name := user.FullName
	if name == "" {
		name = "viajante"
	}
	link := appURL + "/plan/" + trip.TripID.String()
	startStr := startAt.Format("02/01/2006 15:04 MST")

	text := fmt.Sprintf(
		"Olá, %s!\n\nSua viagem \"%s\" começa em menos de %d horas (%s).\n\nAbrir no Baggagi: %s\n\nBom trajeto!\nEquipe Baggagi\n",
		name, trip.TripName, hours, startStr, link,
	)
	htmlBody := fmt.Sprintf(
		`<p>Olá, %s!</p><p>Sua viagem <strong>%s</strong> começa em menos de %d horas (%s).</p><p><a href="%s">Abrir no Baggagi</a></p><p>Bom trajeto!<br/>Equipe Baggagi</p>`,
		html.EscapeString(name),
		html.EscapeString(trip.TripName),
		hours,
		html.EscapeString(startStr),
		html.EscapeString(link),
	)
	return text, htmlBody
}

func parseOrNewUUID(raw string) (uuid.UUID, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return uuid.New(), nil
	}
	id, err := uuid.Parse(raw)
	if err != nil {
		return uuid.Nil, fmt.Errorf("broadcastId inválido: %w", err)
	}
	return id, nil
}

func stripTags(s string) string {
	out := strings.Builder{}
	inTag := false
	for _, r := range s {
		switch {
		case r == '<':
			inTag = true
		case r == '>':
			inTag = false
		case !inTag:
			out.WriteRune(r)
		}
	}
	return strings.TrimSpace(out.String())
}
