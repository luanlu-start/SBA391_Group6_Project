import React, { useState, useEffect, useCallback } from 'react';
import {
    Search,
    Heart,
    User as UserIcon,
    Star,
    Send,
    Clock,
    Truck,
} from 'lucide-react';
import Modal from '../../../components/ui/Modal';
import Button from '../../../components/ui/Button';
import Input from '../../../components/ui/Input';
import Badge from '../../../components/ui/Badge';
import Alert from '../../../components/ui/Alert';
import LoadingSpinner from '../../../components/common/LoadingSpinner';
import ApiErrorNotice from '../../../components/ApiErrorNotice';
import { invitationApi } from '../../../api/invitationApi';
import type { CourierSummary, FavoriteCourier } from '../../../types/invitation';
import { getApiError, type ApiError } from '../../../api/ApiError';

interface InviteCourierModalProps {
    isOpen: boolean;
    onClose: () => void;
    orderId: string | number;
    orderCode: string;
    onSuccess?: () => void;
}

export default function InviteCourierModal({
    isOpen,
    onClose,
    orderId,
    orderCode,
    onSuccess,
}: InviteCourierModalProps): React.ReactElement {
    const [activeTab, setActiveTab] = useState<'favorites' | 'search'>('favorites');
    const [favorites, setFavorites] = useState<FavoriteCourier[]>([]);
    const [searchResults, setSearchResults] = useState<CourierSummary[]>([]);
    const [loadingFavs, setLoadingFavs] = useState<boolean>(false);
    const [searching, setSearching] = useState<boolean>(false);
    const [searchKeyword, setSearchKeyword] = useState<string>('');

    // Selected courier & invitation message
    const [selectedCourierId, setSelectedCourierId] = useState<number | null>(null);
    const [message, setMessage] = useState<string>('');
    const [expiryHours, setExpiryHours] = useState<number>(24);

    const [submitting, setSubmitting] = useState<boolean>(false);
    const [error, setError] = useState<ApiError | null>(null);
    const [successMsg, setSuccessMsg] = useState<string | null>(null);

    // Load favorites
    const loadFavorites = useCallback(async () => {
        setLoadingFavs(true);
        try {
            const res = await invitationApi.getMyFavorites();
            setFavorites(res.data || []);
        } catch (err) {
            console.error('Không thể tải danh sách tài xế yêu thích', err);
        } finally {
            setLoadingFavs(false);
        }
    }, []);

    // Search couriers
    const handleSearch = useCallback(async (keyword: string) => {
        setSearching(true);
        try {
            const res = await invitationApi.searchCouriers({ keyword: keyword.trim() || undefined, size: 20 });
            setSearchResults(res.data?.content || []);
        } catch (err) {
            console.error('Lỗi khi tìm kiếm tài xế', err);
        } finally {
            setSearching(false);
        }
    }, []);

    useEffect(() => {
        if (isOpen) {
            setError(null);
            setSuccessMsg(null);
            setSelectedCourierId(null);
            setMessage('');
            loadFavorites();
            handleSearch('');
        }
    }, [isOpen, loadFavorites, handleSearch]);

    // Toggle favorite
    const handleToggleFavorite = async (courierId: number, e: React.MouseEvent) => {
        e.stopPropagation();
        setError(null);
        try {
            await invitationApi.toggleFavorite(courierId);
            // Refresh both
            await loadFavorites();
            setSearchResults((prev) =>
                prev.map((c) => (c.courierId === courierId ? { ...c, isFavorite: !c.isFavorite } : c))
            );
        } catch (err) {
            console.error('Lỗi khi thay đổi trạng thái yêu thích', err);
            setError(getApiError(err));
        }
    };

    // Submit invitation
    const handleSendInvitation = async () => {
        if (!selectedCourierId) {
            return;
        }
        setSubmitting(true);
        setError(null);
        setSuccessMsg(null);
        try {
            await invitationApi.inviteCourier(orderId, {
                courierId: selectedCourierId,
                message: message.trim() || undefined,
                expiryHours,
            });
            setSuccessMsg('Gửi lời mời trực tiếp thành công! Tài xế sẽ nhận được thông báo ngay lập tức.');
            setTimeout(() => {
                onSuccess?.();
                onClose();
            }, 1200);
        } catch (err) {
            setError(getApiError(err));
        } finally {
            setSubmitting(false);
        }
    };

    const getTierLabel = (tier?: string) => {
        switch (tier) {
            case 'NEW':
                return 'Tân binh';
            case 'BRONZE':
                return 'Đồng';
            case 'SILVER':
                return 'Bạc';
            case 'GOLD':
                return 'Vàng';
            case 'DIAMOND':
                return 'Kim cương';
            default:
                return tier || 'Tài xế';
        }
    };

    const getTierTone = (tier?: string): 'neutral' | 'warning' | 'info' | 'success' => {
        switch (tier) {
            case 'SILVER':
                return 'info';
            case 'GOLD':
                return 'warning';
            case 'DIAMOND':
                return 'success';
            default:
                return 'neutral';
        }
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={() => !submitting && onClose()}
            title="Mời tài xế giao hàng trực tiếp"
            description={`Chọn tài xế tin cậy hoặc từ danh sách yêu thích để giao đơn hàng ${orderCode}`}
            closeDisabled={submitting}
        >
            <div className="ui-stack" style={{ gap: '16px' }}>
                {error && <ApiErrorNotice error={error} />}
                {successMsg && (
                    <Alert tone="success" title="Thành công">
                        {successMsg}
                    </Alert>
                )}

                {/* Tabs */}
                <div
                    style={{
                        display: 'flex',
                        background: 'var(--bg-secondary)',
                        padding: '4px',
                        borderRadius: 'var(--radius-md)',
                        gap: '6px',
                        border: '1px solid var(--border-subtle)',
                    }}
                >
                    <button
                        type="button"
                        onClick={() => setActiveTab('favorites')}
                        style={{
                            flex: 1,
                            padding: '8px 12px',
                            borderRadius: 'var(--radius-sm)',
                            border: 'none',
                            background: activeTab === 'favorites' ? 'var(--primary)' : 'transparent',
                            color: activeTab === 'favorites' ? '#fff' : 'var(--text-secondary)',
                            fontWeight: activeTab === 'favorites' ? 600 : 500,
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '6px',
                            fontSize: '0.875rem',
                            transition: 'all var(--transition-fast)',
                        }}
                    >
                        <Heart size={15} fill={activeTab === 'favorites' ? '#fff' : 'none'} />
                        Bưu tá yêu thích ({favorites.length})
                    </button>
                    <button
                        type="button"
                        onClick={() => setActiveTab('search')}
                        style={{
                            flex: 1,
                            padding: '8px 12px',
                            borderRadius: 'var(--radius-sm)',
                            border: 'none',
                            background: activeTab === 'search' ? 'var(--primary)' : 'transparent',
                            color: activeTab === 'search' ? '#fff' : 'var(--text-secondary)',
                            fontWeight: activeTab === 'search' ? 600 : 500,
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: '6px',
                            fontSize: '0.875rem',
                            transition: 'all var(--transition-fast)',
                        }}
                    >
                        <Search size={15} />
                        Tìm kiếm tài xế
                    </button>
                </div>

                {/* Tab Favorites */}
                {activeTab === 'favorites' && (
                    <div className="ui-stack" style={{ gap: '10px', maxHeight: '280px', overflowY: 'auto' }}>
                        {loadingFavs ? (
                            <div style={{ padding: '24px', textAlign: 'center' }}>
                                <LoadingSpinner text="Đang tải danh sách..." />
                            </div>
                        ) : favorites.length === 0 ? (
                            <div
                                style={{
                                    padding: '24px',
                                    textAlign: 'center',
                                    background: 'var(--bg-secondary)',
                                    borderRadius: 'var(--radius-md)',
                                    color: 'var(--text-muted)',
                                    border: '1px dashed var(--border-subtle)',
                                }}
                            >
                                <Heart size={28} style={{ opacity: 0.4, marginBottom: '6px' }} />
                                <p style={{ margin: 0, fontSize: '0.875rem' }}>
                                    Bạn chưa có tài xế nào trong danh sách yêu thích.
                                </p>
                                <p style={{ margin: '4px 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                                    Chuyển sang tab &quot;Tìm kiếm tài xế&quot; để tìm và thả tim lưu tài xế quen.
                                </p>
                            </div>
                        ) : (
                            favorites.map((fav) => {
                                const isSelected = selectedCourierId === fav.courierId;
                                return (
                                    <div
                                        key={fav.id}
                                        onClick={() => setSelectedCourierId(fav.courierId)}
                                        style={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'space-between',
                                            padding: '12px 14px',
                                            borderRadius: 'var(--radius-md)',
                                            background: isSelected ? 'var(--bg-tertiary)' : 'var(--bg-secondary)',
                                            border: `1.5px solid ${isSelected ? 'var(--primary)' : 'var(--border-subtle)'}`,
                                            cursor: 'pointer',
                                            transition: 'border-color var(--transition-fast)',
                                        }}
                                    >
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                            <div
                                                style={{
                                                    width: '40px',
                                                    height: '40px',
                                                    borderRadius: '50%',
                                                    background: 'var(--brand-soft)',
                                                    color: 'var(--primary)',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    justifyContent: 'center',
                                                    fontWeight: 600,
                                                    fontSize: '0.9rem',
                                                    border: '1px solid var(--brand-border)',
                                                }}
                                            >
                                                {fav.fullName ? fav.fullName.charAt(0).toUpperCase() : <UserIcon size={18} />}
                                            </div>
                                            <div>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                    <span style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: '0.92rem' }}>
                                                        {fav.fullName}
                                                    </span>
                                                    <Badge tone={getTierTone(fav.tier)} dot>
                                                        {getTierLabel(fav.tier)}
                                                    </Badge>
                                                </div>
                                                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                                                    <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
                                                        <Star size={13} fill="var(--accent-amber)" color="var(--accent-amber)" />
                                                        {fav.trustScore ?? 50} điểm
                                                    </span>
                                                    <span>•</span>
                                                    <span>{fav.completedOrders ?? 0} đơn hoàn thành</span>
                                                </div>
                                            </div>
                                        </div>

                                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                            <button
                                                type="button"
                                                onClick={(e) => handleToggleFavorite(fav.courierId, e)}
                                                title="Bỏ yêu thích"
                                                style={{
                                                    background: 'none',
                                                    border: 'none',
                                                    cursor: 'pointer',
                                                    color: 'var(--accent-rose)',
                                                    padding: '6px',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                }}
                                            >
                                                <Heart size={18} fill="currentColor" />
                                            </button>
                                            <input
                                                type="radio"
                                                name="selectedCourier"
                                                checked={isSelected}
                                                onChange={() => setSelectedCourierId(fav.courierId)}
                                                style={{ cursor: 'pointer', accentColor: 'var(--primary)' }}
                                            />
                                        </div>
                                    </div>
                                );
                            })
                        )}
                    </div>
                )}

                {/* Tab Search */}
                {activeTab === 'search' && (
                    <div className="ui-stack" style={{ gap: '10px' }}>
                        <div style={{ position: 'relative' }}>
                            <Input
                                label="Tìm kiếm tài xế"
                                placeholder="Tìm theo tên tài xế, số điện thoại, khu vực hoạt động..."
                                value={searchKeyword}
                                onChange={(e) => {
                                    setSearchKeyword(e.target.value);
                                    handleSearch(e.target.value);
                                }}
                            />
                        </div>

                        <div className="ui-stack" style={{ gap: '10px', maxHeight: '240px', overflowY: 'auto' }}>
                            {searching ? (
                                <div style={{ padding: '24px', textAlign: 'center' }}>
                                    <LoadingSpinner text="Đang tìm kiếm tài xế..." />
                                </div>
                            ) : searchResults.length === 0 ? (
                                <div
                                    style={{
                                        padding: '24px',
                                        textAlign: 'center',
                                        background: 'var(--bg-secondary)',
                                        borderRadius: 'var(--radius-md)',
                                        color: 'var(--text-muted)',
                                        border: '1px dashed var(--border-subtle)',
                                    }}
                                >
                                    <Truck size={28} style={{ opacity: 0.4, marginBottom: '6px' }} />
                                    <p style={{ margin: 0, fontSize: '0.875rem' }}>
                                        Không tìm thấy tài xế nào phù hợp với từ khóa.
                                    </p>
                                </div>
                            ) : (
                                searchResults.map((courier) => {
                                    const isSelected = selectedCourierId === courier.courierId;
                                    return (
                                        <div
                                            key={courier.courierId}
                                            onClick={() => setSelectedCourierId(courier.courierId)}
                                            style={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'space-between',
                                                padding: '12px 14px',
                                                borderRadius: 'var(--radius-md)',
                                                background: isSelected ? 'var(--bg-tertiary)' : 'var(--bg-secondary)',
                                                border: `1.5px solid ${isSelected ? 'var(--primary)' : 'var(--border-subtle)'}`,
                                                cursor: 'pointer',
                                                transition: 'border-color var(--transition-fast)',
                                            }}
                                        >
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                                <div
                                                    style={{
                                                        width: '40px',
                                                        height: '40px',
                                                        borderRadius: '50%',
                                                        background: 'var(--brand-soft)',
                                                        color: 'var(--primary)',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                        fontWeight: 600,
                                                        fontSize: '0.9rem',
                                                        border: '1px solid var(--brand-border)',
                                                    }}
                                                >
                                                    {courier.fullName ? courier.fullName.charAt(0).toUpperCase() : <UserIcon size={18} />}
                                                </div>
                                                <div>
                                                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                        <span style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: '0.92rem' }}>
                                                            {courier.fullName}
                                                        </span>
                                                        <Badge tone={getTierTone(courier.tier)} dot>
                                                            {getTierLabel(courier.tier)}
                                                        </Badge>
                                                    </div>
                                                    <div style={{ display: 'flex', alignItems: 'center', gap: '12px', fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                                                        <span style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
                                                            <Star size={13} fill="var(--accent-amber)" color="var(--accent-amber)" />
                                                            {courier.trustScore ?? 50} điểm
                                                        </span>
                                                        <span>•</span>
                                                        <span>{courier.completedOrders ?? 0} đơn</span>
                                                        {courier.areas && courier.areas.length > 0 && (
                                                            <>
                                                                <span>•</span>
                                                                <span>{courier.areas.slice(0, 2).join(', ')}</span>
                                                            </>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>

                                            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                                                {(() => {
                                                    const isFav = Boolean(courier.isFavorite ?? (courier as unknown as { favorite?: boolean }).favorite);
                                                    return (
                                                        <button
                                                            type="button"
                                                            onClick={(e) => handleToggleFavorite(courier.courierId, e)}
                                                            title={isFav ? 'Bỏ yêu thích' : 'Lưu vào yêu thích'}
                                                            style={{
                                                                background: 'none',
                                                                border: 'none',
                                                                cursor: 'pointer',
                                                                color: isFav ? 'var(--accent-rose)' : 'var(--text-muted)',
                                                                padding: '6px',
                                                                display: 'flex',
                                                                alignItems: 'center',
                                                            }}
                                                        >
                                                            <Heart size={18} fill={isFav ? 'currentColor' : 'none'} />
                                                        </button>
                                                    );
                                                })()}
                                                <input
                                                    type="radio"
                                                    name="selectedCourier"
                                                    checked={isSelected}
                                                    onChange={() => setSelectedCourierId(courier.courierId)}
                                                    style={{ cursor: 'pointer', accentColor: 'var(--primary)' }}
                                                />
                                            </div>
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </div>
                )}

                {/* Message input */}
                <div className="ui-stack" style={{ gap: '8px' }}>
                    <label style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-primary)' }}>
                        Lời nhắn gửi tài xế (Tùy chọn)
                    </label>
                    <textarea
                        rows={2}
                        value={message}
                        onChange={(e) => setMessage(e.target.value)}
                        placeholder="Ví dụ: Kiện hàng cần giao trước 17h, mình đã bọc sẵn chống sốc..."
                        style={{
                            width: '100%',
                            padding: '10px 12px',
                            borderRadius: 'var(--radius-sm)',
                            border: '1px solid var(--border-subtle)',
                            background: 'var(--bg-secondary)',
                            color: 'var(--text-primary)',
                            fontSize: '0.875rem',
                            resize: 'vertical',
                            outline: 'none',
                            fontFamily: 'inherit',
                        }}
                    />
                </div>

                {/* Expiry Hours */}
                <div className="ui-row" style={{ alignItems: 'center', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                    <span style={{ color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <Clock size={14} /> Thời hạn phản hồi:
                    </span>
                    <select
                        value={expiryHours}
                        onChange={(e) => setExpiryHours(Number(e.target.value))}
                        style={{
                            padding: '6px 10px',
                            background: 'var(--bg-secondary)',
                            border: '1px solid var(--border-subtle)',
                            borderRadius: 'var(--radius-sm)',
                            color: 'var(--text-primary)',
                            fontSize: '0.85rem',
                            outline: 'none',
                        }}
                    >
                        <option value={12}>12 giờ</option>
                        <option value={24}>24 giờ (Khuyến nghị)</option>
                        <option value={48}>48 giờ</option>
                    </select>
                </div>

                {/* Action buttons */}
                <div className="ui-row" style={{ justifyContent: 'flex-end', gap: '10px', marginTop: '8px' }}>
                    <Button variant="ghost" onClick={onClose} disabled={submitting}>
                        Đóng
                    </Button>
                    <Button
                        variant="primary"
                        icon={<Send size={16} />}
                        onClick={handleSendInvitation}
                        disabled={!selectedCourierId || submitting}
                        loading={submitting}
                        loadingText="Đang gửi lời mời…"
                    >
                        Gửi lời mời trực tiếp
                    </Button>
                </div>
            </div>
        </Modal>
    );
}
