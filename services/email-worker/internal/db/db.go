package db

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Pool struct {
	*pgxpool.Pool
}

func Connect(ctx context.Context, databaseURL string) (*Pool, error) {
	cfg, err := pgxpool.ParseConfig(databaseURL)
	if err != nil {
		return nil, fmt.Errorf("parse DATABASE_URL: %w", err)
	}
	cfg.MaxConns = 4
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("connect db: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ping db: %w", err)
	}
	return &Pool{Pool: pool}, nil
}

type TripCandidate struct {
	TripID    uuid.UUID
	TripName  string
	StartDate time.Time // date only (UTC midnight of calendar date)
}

type Recipient struct {
	UserID   uuid.UUID
	Email    string
	FullName string
	Timezone string // may be empty
}

// Trips whose start_date is yesterday, today or tomorrow (UTC calendar) —
// enough buffer for timezone ±12h around "day of trip".
func (p *Pool) ListTripsNearStart(ctx context.Context, now time.Time) ([]TripCandidate, error) {
	from := now.UTC().AddDate(0, 0, -1).Truncate(24 * time.Hour)
	to := now.UTC().AddDate(0, 0, 1).Truncate(24 * time.Hour)

	rows, err := p.Query(ctx, `
		SELECT id, name, start_date
		FROM trips
		WHERE start_date IS NOT NULL
		  AND start_date >= $1::date
		  AND start_date <= $2::date
	`, from, to)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []TripCandidate
	for rows.Next() {
		var t TripCandidate
		var startDate time.Time
		if err := rows.Scan(&t.TripID, &t.TripName, &startDate); err != nil {
			return nil, err
		}
		t.StartDate = startDate
		out = append(out, t)
	}
	return out, rows.Err()
}

func (p *Pool) ListTripReminderRecipients(ctx context.Context, tripID uuid.UUID) ([]Recipient, error) {
	rows, err := p.Query(ctx, `
		WITH members AS (
			SELECT t.created_by AS user_id FROM trips t WHERE t.id = $1
			UNION
			SELECT tu.user_id FROM trip_users tu WHERE tu.trip_id = $1
		)
		SELECT u.id, u.email, COALESCE(u.full_name, ''), COALESCE(u.timezone, '')
		FROM members m
		JOIN users u ON u.id = m.user_id
		WHERE u.deleted_at IS NULL
		  AND u.email IS NOT NULL
		  AND TRIM(u.email) <> ''
		  AND (u.account_status IS NULL OR LOWER(u.account_status) = 'active')
		  AND NOT EXISTS (
		      SELECT 1 FROM user_email_preferences p
		      WHERE p.user_id = u.id AND p.trip_reminders = false
		  )
	`, tripID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []Recipient
	for rows.Next() {
		var r Recipient
		if err := rows.Scan(&r.UserID, &r.Email, &r.FullName, &r.Timezone); err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

func (p *Pool) ListProductUpdateRecipients(ctx context.Context) ([]Recipient, error) {
	rows, err := p.Query(ctx, `
		SELECT u.id, u.email, COALESCE(u.full_name, ''), COALESCE(u.timezone, '')
		FROM users u
		WHERE u.deleted_at IS NULL
		  AND u.email IS NOT NULL
		  AND TRIM(u.email) <> ''
		  AND (u.account_status IS NULL OR LOWER(u.account_status) = 'active')
		  AND NOT EXISTS (
		      SELECT 1 FROM user_email_preferences p
		      WHERE p.user_id = u.id AND p.email_updates = false
		  )
		ORDER BY u.id
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []Recipient
	for rows.Next() {
		var r Recipient
		if err := rows.Scan(&r.UserID, &r.Email, &r.FullName, &r.Timezone); err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

type ExpiringDocument struct {
	ID         uuid.UUID
	UserID     uuid.UUID
	Kind       string
	Name       string // nome customizado; vazio para tipos fixos (PASSPORT/VISA/INTERNATIONAL_LICENSE)
	ExpiryDate time.Time
	Email      string
	FullName   string
}

// ListExpiringDocuments retorna documentos com alerta habilitado cujo expiry_date
// esteja exatamente a N dias de "today" (UTC), para N em dayOffsets — e cujo
// usuário não tenha desligado document_expiry_alerts.
func (p *Pool) ListExpiringDocuments(ctx context.Context, today time.Time, dayOffsets []int32) ([]ExpiringDocument, error) {
	rows, err := p.Query(ctx, `
		SELECT d.id, d.user_id, d.kind, COALESCE(d.name, ''), d.expiry_date, u.email, COALESCE(u.full_name, '')
		FROM user_document_expiry d
		JOIN users u ON u.id = d.user_id
		WHERE d.alert_enabled = TRUE
		  AND d.expiry_date IS NOT NULL
		  AND (d.expiry_date - $1::date) = ANY($2::int[])
		  AND u.deleted_at IS NULL
		  AND u.email IS NOT NULL
		  AND TRIM(u.email) <> ''
		  AND (u.account_status IS NULL OR LOWER(u.account_status) = 'active')
		  AND NOT EXISTS (
		      SELECT 1 FROM user_email_preferences p
		      WHERE p.user_id = u.id AND p.document_expiry_alerts = false
		  )
	`, today, dayOffsets)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []ExpiringDocument
	for rows.Next() {
		var d ExpiringDocument
		var expiryDate time.Time
		if err := rows.Scan(&d.ID, &d.UserID, &d.Kind, &d.Name, &expiryDate, &d.Email, &d.FullName); err != nil {
			return nil, err
		}
		d.ExpiryDate = expiryDate
		out = append(out, d)
	}
	return out, rows.Err()
}

func (p *Pool) AlreadySent(ctx context.Context, userID uuid.UUID, emailType string, referenceID *uuid.UUID) (bool, error) {
	var exists bool
	err := p.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1 FROM email_notification_log
			WHERE user_id = $1
			  AND email_type = $2
			  AND (
			    ($3::uuid IS NULL AND reference_id IS NULL)
			    OR reference_id = $3
			  )
		)
	`, userID, emailType, referenceID).Scan(&exists)
	return exists, err
}

func (p *Pool) RecordSent(ctx context.Context, userID uuid.UUID, emailType string, referenceID *uuid.UUID, messageID string) error {
	_, err := p.Exec(ctx, `
		INSERT INTO email_notification_log (id, user_id, email_type, reference_id, channel, message_id, status, sent_at)
		SELECT gen_random_uuid(), $1, $2, $3, 'EMAIL', $4, 'SENT', now()
		WHERE NOT EXISTS (
			SELECT 1 FROM email_notification_log
			WHERE user_id = $1
			  AND email_type = $2
			  AND (
			    ($3::uuid IS NULL AND reference_id IS NULL)
			    OR reference_id = $3
			  )
		)
	`, userID, emailType, referenceID, messageID)
	return err
}
