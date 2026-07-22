package mail

import (
	"context"
	"fmt"
	"strings"

	"github.com/aws/aws-sdk-go-v2/aws"
	awsconfig "github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/ses"
	"github.com/aws/aws-sdk-go-v2/service/ses/types"
)

type Sender struct {
	client    *ses.Client
	fromEmail string
	fromName  string
	enabled   bool
}

func New(ctx context.Context, region, fromEmail, fromName string, enabled bool) (*Sender, error) {
	cfg, err := awsconfig.LoadDefaultConfig(ctx, awsconfig.WithRegion(region))
	if err != nil {
		return nil, fmt.Errorf("aws config: %w", err)
	}
	return &Sender{
		client:    ses.NewFromConfig(cfg),
		fromEmail: fromEmail,
		fromName:  fromName,
		enabled:   enabled,
	}, nil
}

func (s *Sender) Ready() bool {
	return s.enabled && strings.TrimSpace(s.fromEmail) != ""
}

// Send returns SES message id, or ("", nil) when SES is disabled.
func (s *Sender) Send(ctx context.Context, to, subject, textBody, htmlBody string) (string, error) {
	if !s.Ready() {
		return "", nil
	}
	source := formatSource(s.fromEmail, s.fromName)
	body := &types.Body{
		Text: &types.Content{Charset: aws.String("UTF-8"), Data: aws.String(textBody)},
	}
	if strings.TrimSpace(htmlBody) != "" {
		body.Html = &types.Content{Charset: aws.String("UTF-8"), Data: aws.String(htmlBody)}
	}

	out, err := s.client.SendEmail(ctx, &ses.SendEmailInput{
		Source: aws.String(source),
		Destination: &types.Destination{
			ToAddresses: []string{strings.TrimSpace(to)},
		},
		Message: &types.Message{
			Subject: &types.Content{Charset: aws.String("UTF-8"), Data: aws.String(subject)},
			Body:    body,
		},
	})
	if err != nil {
		return "", err
	}
	if out.MessageId == nil {
		return "", nil
	}
	return *out.MessageId, nil
}

func formatSource(email, name string) string {
	name = strings.ReplaceAll(name, `"`, "")
	name = strings.ReplaceAll(name, "<", "")
	name = strings.ReplaceAll(name, ">", "")
	if strings.TrimSpace(name) == "" {
		return email
	}
	return fmt.Sprintf(`"%s" <%s>`, name, email)
}
