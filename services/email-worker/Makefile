.PHONY: build tidy test invoke-reminders invoke-product package deploy

build:
	GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -tags lambda.norpc -o bootstrap ./cmd/lambda
	# SAM Makefile build expects artifact in .aws-sam or cwd; zip for sam package
	chmod +x bootstrap

# Usado pelo `sam build` (Metadata BuildMethod: makefile → target "build-EmailWorkerFunction")
build-EmailWorkerFunction:
	GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -tags lambda.norpc -o $(ARTIFACTS_DIR)/bootstrap ./cmd/lambda
	chmod +x $(ARTIFACTS_DIR)/bootstrap

tidy:
	go mod tidy

test:
	go test ./...

invoke-reminders:
	aws lambda invoke --function-name baggagi-email-worker \
	  --payload '{"action":"trip_reminders"}' \
	  --cli-binary-format raw-in-base64-out /tmp/email-worker-out.json \
	  && cat /tmp/email-worker-out.json && echo

# Ex.: make invoke-product SUBJECT="Novidade" TEXT="Olá…"
invoke-product:
	aws lambda invoke --function-name baggagi-email-worker \
	  --payload "{\"action\":\"product_update\",\"subject\":\"$${SUBJECT:-Novidade Baggagi}\",\"textBody\":\"$${TEXT:-Atualização do produto.}\",\"dryRun\":$${DRY_RUN:-false}}" \
	  --cli-binary-format raw-in-base64-out /tmp/email-worker-out.json \
	  && cat /tmp/email-worker-out.json && echo

deploy:
	sam build
	sam deploy --guided
