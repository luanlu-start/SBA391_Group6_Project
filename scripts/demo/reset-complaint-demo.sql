-- Đưa dữ liệu demo khiếu nại, Quỹ bảo đảm và uy tín về trạng thái ban đầu (SQL Server, chỉ dùng cho database dev).
-- Sau khi chạy, khởi động lại server để DisputeDemoSeeder tạo lại dữ liệu. Xem docs/KICH-BAN-DEMO-THUANTC.md.
-- Script hoàn tác phần tiền các giao dịch demo đã cộng vào ví, rồi xóa giao dịch, nên sổ cái và ví vẫn khớp nhau.
-- Quỹ bảo đảm được đặt lại về 0 (xóa mọi giao dịch trích và chi quỹ).
-- Mọi lệnh nằm trong một transaction: nếu đơn demo đã bị tham chiếu ở bảng khác, script dừng và không thay đổi gì.
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;

BEGIN TRANSACTION;

DECLARE @orders TABLE (id bigint PRIMARY KEY);
INSERT INTO @orders SELECT id FROM orders WHERE order_code LIKE 'DEMO-%';

DECLARE @transactions TABLE (id bigint PRIMARY KEY);
INSERT INTO @transactions SELECT id FROM ledger_transactions
WHERE order_id IN (SELECT id FROM @orders) OR type IN ('GUARANTEE_FUND_CONTRIBUTION', 'GUARANTEE_FUND_PAYOUT');

UPDATE w SET available = w.available - moved.amount
FROM wallets w
JOIN (SELECT e.account_code, SUM(e.credit - e.debit) AS amount
      FROM ledger_entries e
      WHERE e.transaction_id IN (SELECT id FROM @transactions) AND e.account_code LIKE 'USER_WALLET[_]%'
      GROUP BY e.account_code) moved ON moved.account_code = CONCAT('USER_WALLET_', w.user_id);

DELETE FROM ledger_entries WHERE transaction_id IN (SELECT id FROM @transactions);
DELETE FROM ledger_transactions WHERE id IN (SELECT id FROM @transactions);
DELETE FROM guarantee_fund_accounts;

DECLARE @complaints TABLE (id bigint PRIMARY KEY);
INSERT INTO @complaints SELECT id FROM complaints WHERE order_id IN (SELECT id FROM @orders);

DELETE FROM compensations WHERE complaint_id IN (SELECT id FROM @complaints);
DELETE FROM complaint_evidence WHERE complaint_id IN (SELECT id FROM @complaints);
DELETE FROM complaint_messages WHERE complaint_id IN (SELECT id FROM @complaints);
DELETE FROM complaints WHERE id IN (SELECT id FROM @complaints);
DELETE FROM escrows WHERE order_id IN (SELECT id FROM @orders);
DELETE FROM orders WHERE id IN (SELECT id FROM @orders);

-- Người giao demo trở lại hồ sơ ban đầu do UserDemoSeeder tạo; điểm được tính lại khi khiếu nại demo đóng.
DECLARE @courier bigint = (SELECT id FROM users WHERE email = 'courier@fptpost.vn');
DELETE FROM trust_score_history WHERE courier_id = @courier;
DELETE FROM trust_scores WHERE courier_id = @courier;
UPDATE courier_profiles SET tier = 'SILVER', trust_score = 80, completed_orders = 25 WHERE user_id = @courier;

DELETE FROM notification_deliveries WHERE event_id IN (
    SELECT id FROM notification_events
    WHERE event_key LIKE 'complaint:%' OR event_key LIKE 'compensation:%' OR event_key LIKE 'trust:%');
DELETE FROM notification_events
WHERE event_key LIKE 'complaint:%' OR event_key LIKE 'compensation:%' OR event_key LIKE 'trust:%';

COMMIT TRANSACTION;

SELECT (SELECT COUNT(*) FROM orders WHERE order_code LIKE 'DEMO-%') AS demo_orders,
       (SELECT COUNT(*) FROM complaints) AS complaints,
       (SELECT COUNT(*) FROM compensations) AS compensations,
       (SELECT COUNT(*) FROM ledger_transactions WHERE type LIKE 'GUARANTEE[_]FUND[_]%') AS fund_transactions;
