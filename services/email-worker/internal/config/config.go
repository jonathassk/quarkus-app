package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	DatabaseURL   string
	SESRegion     string
	FromEmail     string
	FromName      string
	AppPublicURL  string
	SESEnabled    bool
	ReminderHours int // janela: faltam menos de N horas para o início do dia da viagem
}

func Load() (Config, error) {
	cfg := Config{
		DatabaseURL:   strings.TrimSpace(os.Getenv("DATABASE_URL")),
		SESRegion:     envOr("AWS_SES_REGION", envOr("AWS_REGION", "us-east-1")),
		FromEmail:     strings.TrimSpace(os.Getenv("SES_FROM_EMAIL")),
		FromName:      envOr("SES_FROM_NAME", "Baggagi"),
		AppPublicURL:  strings.TrimRight(envOr("APP_PUBLIC_URL", "https://baggagi.com"), "/"),
		SESEnabled:    parseBool(envOr("SES_ENABLED", "false")),
		ReminderHours: parseInt(envOr("EMAIL_TRIP_REMINDER_HOURS", "12"), 12),
	}
	if cfg.DatabaseURL == "" {
		return cfg, fmt.Errorf("DATABASE_URL is required")
	}
	if cfg.SESEnabled && cfg.FromEmail == "" {
		return cfg, fmt.Errorf("SES_FROM_EMAIL is required when SES_ENABLED=true")
	}
	return cfg, nil
}

func envOr(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}

func parseBool(s string) bool {
	b, err := strconv.ParseBool(s)
	return err == nil && b
}

func parseInt(s string, fallback int) int {
	n, err := strconv.Atoi(s)
	if err != nil {
		return fallback
	}
	return n
}
