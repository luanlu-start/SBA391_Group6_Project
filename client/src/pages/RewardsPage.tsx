import { useEffect, useState } from 'react';
import { Award, Copy, Link2, RefreshCw } from 'lucide-react';
import { Navigate } from 'react-router-dom';
import { getApiError, type ApiError } from '../api/ApiError';
import { useAuthentication } from '../auth/useAuthentication';
import ApiErrorNotice from '../components/ApiErrorNotice';
import Badge from '../components/ui/Badge';
import Button from '../components/ui/Button';
import EmptyState from '../components/ui/EmptyState';
import DataTable, { type TableColumn } from '../components/ui/DataTable';
import Pagination from '../components/ui/Pagination';
import PageHeader from '../components/ui/PageHeader';
import SectionCard from '../components/ui/SectionCard';
import ReferralCodeForm from '../features/rewards/components/ReferralCodeForm';
import { rewardsService } from '../services/rewardsService';
import type { BadgesResponse, ReferralDetail, ReferralResponse } from '../types/rewards';

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' });
const weekDate = new Intl.DateTimeFormat('vi-VN', { timeZone: 'UTC', dateStyle: 'medium' });
const dateTime = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short',
});
const referralStates = {
  WAITING_VERIFICATION: { label: 'Chờ xác thực email', tone: 'warning' },
  WAITING_FIRST_ORDER: { label: 'Chờ hoàn tất đơn đầu tiên', tone: 'info' },
  REWARDED: { label: 'Đã nhận thưởng', tone: 'success' },
  ACCOUNT_INACTIVE: { label: 'Tài khoản chưa hoạt động', tone: 'neutral' },
} as const;

function ReferralState({ status }: { status: ReferralDetail['status'] }) {
  const state = referralStates[status];
  return <Badge tone={state.tone}>{state.label}</Badge>;
}

const inviteeColumns: TableColumn<ReferralDetail>[] = [
  { key: 'person', header: 'Người được mời', render: person => <div>
    <strong>{person.fullName}</strong><p className="ui-muted">{person.maskedEmail}</p>
  </div> },
  { key: 'registeredAt', header: 'Ghi nhận lúc', render: person => dateTime.format(new Date(person.registeredAt)) },
  { key: 'status', header: 'Trạng thái', render: person => <ReferralState status={person.status} /> },
  { key: 'reward', header: 'Thưởng của bạn', align: 'right', render: person => person.rewardedAt
    ? <div><strong>{money.format(person.rewardAmount ?? 0)}</strong>
      <p className="ui-muted">{dateTime.format(new Date(person.rewardedAt))}</p></div>
    : <span className="ui-muted">Chưa nhận</span> },
];

export default function RewardsPage() {
  const { user, initializing } = useAuthentication();
  if (initializing) return <p className="ui-muted" role="status">Đang kiểm tra phiên đăng nhập…</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <RewardsContent key={user.id} />;
}

function RewardsContent() {
  const [attempt, setAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const [data, setData] = useState<{ badges: BadgesResponse; referral: ReferralResponse }>();
  const [error, setError] = useState<ApiError>();
  const [notice, setNotice] = useState('');
  const loading = !data && !error;

  useEffect(() => {
    let active = true;
    Promise.all([rewardsService.badges(), rewardsService.referral(page)])
      .then(([badges, referral]) => {
        if (active) setData({ badges: badges.data, referral: referral.data });
      })
      .catch(cause => { if (active) setError(getApiError(cause)); });
    return () => { active = false; };
  }, [attempt, page]);

  function changePage(nextPage: number) {
    setData(undefined);
    setError(undefined);
    setNotice('');
    setPage(nextPage);
  }

  function reload() {
    setData(undefined);
    setError(undefined);
    setNotice('');
    setAttempt(value => value + 1);
  }

  function appliedReferral(referredBy: ReferralDetail) {
    setData(current => current ? {
      ...current, referral: { ...current.referral, referredBy, eligibility: 'ALREADY_RECORDED' },
    } : current);
  }

  async function copy(value: string, message: string) {
    try {
      await navigator.clipboard.writeText(value);
      setNotice(message);
    } catch {
      setNotice('Chưa sao chép được. Bạn có thể chọn và sao chép mã hoặc liên kết bên dưới.');
    }
  }

  const referral = data?.referral;
  const badges = data?.badges;
  const inviteUrl = referral && new URL('/register?ref=' + encodeURIComponent(referral.code), window.location.origin).href;

  return <div className="ui-stack" aria-busy={loading}>
    <PageHeader eyebrow="Tài khoản" title="Huy hiệu và giới thiệu"
      description="Xem thành tích giao hàng và thưởng khi mời bạn bè tham gia FPost." />
    {loading && <p className="ui-muted" role="status">Đang tải huy hiệu và thưởng…</p>}
    {error && <ApiErrorNotice error={error} />}
    {!loading && <div className="ui-form-actions">
      <Button variant="secondary" size="sm" onClick={reload} icon={<RefreshCw size={16} />}>Tải lại</Button>
    </div>}
    {referral && inviteUrl && <SectionCard title="Giới thiệu bạn bè"
      description={`Bạn và người được mời mỗi người nhận ${money.format(referral.rewardPerPerson)} sau khi họ hoàn tất đơn đầu tiên.`}>
      <div className="ui-stack">
        <div>
          <p className="ui-muted">Mã giới thiệu của bạn</p>
          <p className="ui-code-example">{referral.code}</p>
        </div>
        <p className="ui-code-example">{inviteUrl}</p>
        <div className="ui-form-actions">
          <Button variant="secondary" onClick={() => void copy(referral.code, 'Đã sao chép mã giới thiệu.')}
            icon={<Copy size={16} />}>Sao chép mã</Button>
          <Button variant="secondary" onClick={() => void copy(inviteUrl, 'Đã sao chép liên kết mời.')}
            icon={<Link2 size={16} />}>Sao chép liên kết</Button>
        </div>
        {notice && <p className="ui-muted" role="status">{notice}</p>}
        <dl className="ui-form-grid">
          <div><dt className="ui-muted">Đã dùng mã của bạn</dt><dd><strong>{referral.invited}</strong></dd></div>
          <div><dt className="ui-muted">Đang chờ nhận thưởng</dt><dd><strong>{referral.pending}</strong></dd></div>
          <div><dt className="ui-muted">Đã nhận thưởng</dt><dd><strong>{referral.rewarded} lượt</strong></dd></div>
          <div><dt className="ui-muted">Tiền thưởng giới thiệu của bạn</dt><dd><strong>{money.format(referral.earnedAmount)}</strong></dd></div>
        </dl>
      </div>
    </SectionCard>}
    {referral && <SectionCard title="Người giới thiệu của bạn"
      description="Mỗi tài khoản được dùng một mã giới thiệu trước khi hoàn tất đơn đầu tiên.">
      {referral.referredBy ? <div className="ui-stack">
        <p role="status">Đã ghi nhận mã <strong>{referral.referredBy.referralCode}</strong> cho tài khoản của bạn.</p>
        <div><strong>{referral.referredBy.fullName}</strong>
          <p className="ui-muted">{referral.referredBy.maskedEmail}</p></div>
        <p className="ui-muted">Ghi nhận lúc {dateTime.format(new Date(referral.referredBy.registeredAt))}</p>
        <div><ReferralState status={referral.referredBy.status} /></div>
        {referral.referredBy.rewardedAt
          ? <p>Bạn đã nhận <strong>{money.format(referral.referredBy.rewardAmount ?? 0)}</strong> vào ví lúc{' '}
            {dateTime.format(new Date(referral.referredBy.rewardedAt))}.</p>
          : <p className="ui-muted">Hoàn tất đơn đầu tiên đủ điều kiện để bạn và người giới thiệu nhận thưởng.</p>}
      </div> : referral.eligibility === 'ELIGIBLE'
        ? <ReferralCodeForm onApplied={appliedReferral} onEligibilityChanged={reload} />
        : <p className="ui-muted">{referral.eligibility === 'FIRST_ORDER_COMPLETED'
          ? 'Bạn đã hoàn tất đơn đầu tiên nên thời hạn nhập mã giới thiệu đã kết thúc.'
          : 'Tài khoản hiện chưa đủ điều kiện để nhập mã giới thiệu.'}</p>}
    </SectionCard>}
    {referral && <SectionCard title="Người đã dùng mã của bạn">
      <div className="ui-stack">
        <DataTable caption="Danh sách người được bạn giới thiệu" columns={inviteeColumns}
          rows={referral.invitees.content} rowKey={person => person.userId}
          emptyState={<EmptyState title="Chưa có người dùng mã giới thiệu"
            description="Gửi liên kết mời cho bạn bè. Người dùng mã của bạn sẽ xuất hiện tại đây." />} />
        <Pagination page={referral.invitees.page} size={referral.invitees.size}
          totalElements={referral.invitees.totalElements} totalPages={referral.invitees.totalPages}
          onPageChange={changePage} disabled={loading} />
      </div>
    </SectionCard>}
    {badges && <SectionCard title="Huy hiệu của tôi">
      <div className="ui-stack">
        {badges.courier && <div>
          <p><strong>{badges.weeklyDeliveries}/{badges.weeklyTarget}</strong> đơn giao thành công tuần này</p>
          <p className="ui-muted">Tuần từ {weekDate.format(new Date(badges.weekStart))}.
            {badges.weeklyReward > 0 && ` Thưởng khi đạt mốc tuần này: ${money.format(badges.weeklyReward)}.`}</p>
        </div>}
        {badges.badges.length ? badges.badges.map(badge => <div key={badge.code + (badge.weekStart ?? '')}>
          <Badge tone="success"><Award size={15} aria-hidden="true" />{badge.title}</Badge>
          {badge.weekStart && <p className="ui-muted">Tuần từ {weekDate.format(new Date(badge.weekStart))}
            {badge.rewardAmount > 0 && ` · ${money.format(badge.rewardAmount)}`}</p>}
        </div>) : <EmptyState title="Bạn chưa có huy hiệu" description="Huy hiệu sẽ xuất hiện ở đây khi bạn đạt mốc thành tích."
          icon={<Award size={24} />} />}
      </div>
    </SectionCard>}
  </div>;
}
