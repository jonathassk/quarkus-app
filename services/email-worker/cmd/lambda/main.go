package main

import (
	"context"
	"encoding/json"
	"log/slog"
	"os"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/baggagi/email-worker/internal/config"
	"github.com/baggagi/email-worker/internal/db"
	"github.com/baggagi/email-worker/internal/jobs"
	"github.com/baggagi/email-worker/internal/mail"
)

// Event payload:
//
//	{"action":"trip_reminders"}
//	{"action":"document_expiry_reminders"}
//	{"action":"product_update","subject":"...","textBody":"...","htmlBody":"...","broadcastId":"...","dryRun":false}
//	{"action":"send_direct","toEmail":"...","subject":"...","textBody":"...","htmlBody":"..."}
type Event struct {
	Action      string `json:"action"`
	ToEmail     string `json:"toEmail"`
	Subject     string `json:"subject"`
	TextBody    string `json:"textBody"`
	HTMLBody    string `json:"htmlBody"`
	BroadcastID string `json:"broadcastId"`
	DryRun      bool   `json:"dryRun"`
}

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))

	cfg, err := config.Load()
	if err != nil {
		log.Error("config", "err", err)
		os.Exit(1)
	}

	ctx := context.Background()
	pool, err := db.Connect(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Error("db", "err", err)
		os.Exit(1)
	}
	defer pool.Close()

	mailer, err := mail.New(ctx, cfg.SESRegion, cfg.FromEmail, cfg.FromName, cfg.SESEnabled)
	if err != nil {
		log.Error("ses", "err", err)
		os.Exit(1)
	}

	runner := &jobs.Runner{CFG: cfg, DB: pool, Mailer: mailer, Log: log}

	lambda.Start(func(ctx context.Context, raw json.RawMessage) (jobs.Result, error) {
		var ev Event
		if len(raw) > 0 && string(raw) != "null" {
			if err := json.Unmarshal(raw, &ev); err != nil {
				// EventBridge Scheduled Event sem detail customizado → trip_reminders
				ev.Action = "trip_reminders"
			}
		}
		if ev.Action == "" {
			ev.Action = "trip_reminders"
		}

		log.Info("invoke", "action", ev.Action)

		switch ev.Action {
		case "trip_reminders":
			return runner.TripReminders(ctx)
		case "document_expiry_reminders":
			return runner.DocumentExpiryReminders(ctx)
		case "product_update":
			return runner.ProductUpdate(ctx, jobs.ProductUpdateInput{
				Subject:     ev.Subject,
				TextBody:    ev.TextBody,
				HTMLBody:    ev.HTMLBody,
				BroadcastID: ev.BroadcastID,
				DryRun:      ev.DryRun,
			})
		case "send_direct":
			return runner.SendDirect(ctx, jobs.SendDirectInput{
				ToEmail:  ev.ToEmail,
				Subject:  ev.Subject,
				TextBody: ev.TextBody,
				HTMLBody: ev.HTMLBody,
			})
		default:
			return jobs.Result{Action: ev.Action}, jsonError("unknown action: " + ev.Action)
		}
	})
}

type plainError string

func (e plainError) Error() string { return string(e) }

func jsonError(msg string) error { return plainError(msg) }
