-- Políticas comerciais da agência + solicitação de alteração

ALTER TABLE agencies
    ADD COLUMN IF NOT EXISTS min_margin_bps INT,
    ADD COLUMN IF NOT EXISTS min_service_fee_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS allow_below_minimum BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS require_discount_reason BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS max_discount_bps INT;

COMMENT ON COLUMN agencies.min_margin_bps IS 'Margem mínima sobre a venda em basis points (1000 = 10%). NULL = sem piso.';
COMMENT ON COLUMN agencies.min_service_fee_minor IS 'Taxa de serviço mínima em minor units da moeda da agência.';
COMMENT ON COLUMN agencies.allow_below_minimum IS 'Se true, consultor pode continuar abaixo do mínimo com justificativa.';
COMMENT ON COLUMN agencies.require_discount_reason IS 'Exige motivo em todo desconto/ajuste.';
COMMENT ON COLUMN agencies.max_discount_bps IS 'Desconto máximo percentual em bps sobre o preço. NULL = sem teto.';

ALTER TABLE proposal_versions
    ADD COLUMN IF NOT EXISTS change_request_types JSONB,
    ADD COLUMN IF NOT EXISTS change_request_message TEXT,
    ADD COLUMN IF NOT EXISTS change_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS change_requested_by_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS change_requested_by_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS below_minimum_justification TEXT;

ALTER TABLE agency_proposals
    ADD COLUMN IF NOT EXISTS recommendation_note TEXT;

-- Garante price_visibility já existe (V33); sem alteração se presente.
