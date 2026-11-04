import { useEffect, useId, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { Check, ChevronDown, LoaderCircle, X } from 'lucide-react';
import FormField from '../../../components/ui/FormField';
import type { AuditFilterField, AuditFilterOption } from '../../../types/audit';
import useAuditFilterOptions from '../hooks/useAuditFilterOptions';

interface AuditFilterChooserProps {
  field: AuditFilterField;
  label: string;
  allLabel: string;
  placeholder: string;
  hint: string;
  value: string;
  onChange: (value: string) => void;
  onBlur: () => void;
  error?: string;
  entityType?: string;
  refreshToken: number;
}

const knownLabels: Partial<Record<AuditFilterField, Record<string, string>>> = {
  action: { PARAMETER_UPDATED: 'Cập nhật tham số' },
  entityType: { SYSTEM_PARAMETER: 'Tham số hệ thống' },
};

function presentOption(field: AuditFilterField, option: AuditFilterOption): AuditFilterOption {
  const label = knownLabels[field]?.[option.value] ?? option.label;
  return { ...option, label, description: option.description || (label !== option.value ? option.value : undefined) };
}

function searchAlias(field: AuditFilterField, search: string) {
  const value = search.trim();
  if (!value) return undefined;
  const normalized = (text: string) => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').toLowerCase();
  const matches = Object.entries(knownLabels[field] ?? {}).filter(([, label]) => normalized(label).includes(normalized(value)));
  return matches.length === 1 ? matches[0][0] : undefined;
}

/** Search text only locates suggestions; a filter changes after explicitly choosing an option. */
export default function AuditFilterChooser({ field, label, allLabel, placeholder, hint, value, onChange, onBlur,
  error, entityType, refreshToken }: AuditFilterChooserProps) {
  const id = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const knownOptions = useRef(new Map<string, AuditFilterOption>());
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [activeIndex, setActiveIndex] = useState(-1);
  const [retryToken, setRetryToken] = useState(0);
  const { result, loading, error: optionsError } = useAuditFilterOptions(field, search.trim(), searchAlias(field, search),
    field === 'entityId' ? entityType : undefined, refreshToken, retryToken);
  const options = [{ value: '', label: allLabel }, ...(result?.options ?? []).map(option => presentOption(field, option))];
  const selected = value ? knownOptions.current.get(value) ?? presentOption(field, { value, label: value }) : null;
  const describedBy = [`${id}-hint`, error && `${id}-error`, open && selected && `${id}-selected`].filter(Boolean).join(' ');

  useEffect(() => {
    for (const option of result?.options ?? []) knownOptions.current.set(option.value, presentOption(field, option));
  }, [result, field]);

  useEffect(() => { setActiveIndex(-1); }, [search, result, entityType]);

  useEffect(() => {
    if (open && activeIndex >= 0) document.getElementById(`${id}-option-${activeIndex}`)?.scrollIntoView({ block: 'nearest' });
  }, [open, activeIndex, id]);

  function close() {
    setOpen(false);
    setSearch('');
    setActiveIndex(-1);
  }

  function choose(option: AuditFilterOption) {
    if (option.value) knownOptions.current.set(option.value, option);
    onChange(option.value);
    close();
    inputRef.current?.focus();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      setOpen(true);
      setActiveIndex(previous => event.key === 'ArrowDown'
        ? Math.min(previous + 1, options.length - 1) : previous < 0 ? options.length - 1 : Math.max(previous - 1, 0));
    } else if (event.key === 'Home' && open) {
      event.preventDefault(); setActiveIndex(0);
    } else if (event.key === 'End' && open) {
      event.preventDefault(); setActiveIndex(options.length - 1);
    } else if (event.key === 'Enter' && open) {
      event.preventDefault();
      if (activeIndex >= 0 && options[activeIndex]) choose(options[activeIndex]);
    } else if (event.key === 'Escape' && open) {
      event.preventDefault(); event.stopPropagation(); close();
    } else if (event.key === 'Tab') close();
  }

  return <div className="audit-chooser" onBlur={event => {
    if (!event.currentTarget.contains(event.relatedTarget)) { close(); onBlur(); }
  }}>
    <FormField id={id} label={label} hint={hint} error={error}>
      <div className="audit-chooser-control">
        <input ref={inputRef} id={id} role="combobox" aria-autocomplete="list" aria-expanded={open}
          aria-controls={open ? `${id}-options` : undefined}
          aria-activedescendant={open && activeIndex >= 0 ? `${id}-option-${activeIndex}` : undefined}
          aria-describedby={describedBy} aria-invalid={error ? true : undefined}
          autoComplete="off" maxLength={100} className="ui-control audit-chooser-input"
          placeholder={open ? placeholder : allLabel} value={open ? search : selected?.label ?? ''}
          onFocus={() => setOpen(true)} onClick={() => setOpen(true)} onKeyDown={handleKeyDown}
          onChange={event => { setSearch(event.target.value); setOpen(true); setActiveIndex(-1); }} />
        <div className="audit-chooser-actions">
          {value && <button type="button" className="audit-chooser-icon" aria-label={`Xóa ${label.toLowerCase()}`}
            onMouseDown={event => event.preventDefault()} onClick={() => { onChange(''); setSearch(''); inputRef.current?.focus(); }}>
            <X size={15} aria-hidden="true" />
          </button>}
          <button type="button" className="audit-chooser-icon" tabIndex={-1}
            aria-label={`${open ? 'Đóng' : 'Mở'} danh sách ${label.toLowerCase()}`}
            onMouseDown={event => event.preventDefault()} onClick={() => {
              if (open) close(); else { inputRef.current?.focus(); setOpen(true); }
            }}><ChevronDown size={17} aria-hidden="true" /></button>
        </div>
        {open && <div className="audit-chooser-popup">
          {selected && <p id={`${id}-selected`} className="audit-chooser-selected">Đang chọn: <strong>{selected.label}</strong></p>}
          <ul id={`${id}-options`} className="audit-chooser-options" role="listbox" aria-label={label}
            aria-busy={loading || undefined}>
            {options.map((option, index) => <li key={option.value} id={`${id}-option-${index}`} role="option"
              aria-selected={option.value === value} className={`audit-chooser-option${index === activeIndex ? ' is-active' : ''}`}
              onMouseDown={event => event.preventDefault()} onMouseMove={() => setActiveIndex(index)} onClick={() => choose(option)}>
              <span><span className="audit-chooser-option-label">{option.label}</span>
                {option.description && <span className="audit-chooser-option-description">{option.description}</span>}</span>
              {option.value === value && <Check size={16} aria-hidden="true" />}
            </li>)}
          </ul>
          {loading ? <p className="audit-chooser-message" role="status"><LoaderCircle size={15} className="ui-spin" aria-hidden="true" />Đang tải lựa chọn…</p>
            : optionsError ? <div className="audit-chooser-message audit-chooser-error" role="alert">
              <span>Chưa thể tải lựa chọn. Bộ lọc đang chọn vẫn được giữ.</span>
              <button type="button" onClick={() => setRetryToken(previous => previous + 1)}>Thử lại</button>
            </div> : <p className="audit-chooser-message" role="status">
              {result?.hasMore ? 'Còn lựa chọn khác. Nhập thêm từ khóa để thu hẹp danh sách.'
                : !result?.options.length ? 'Không có lựa chọn phù hợp trong nhật ký.' : 'Chọn một mục để dùng làm điều kiện tra cứu.'}
            </p>}
        </div>}
      </div>
    </FormField>
  </div>;
}
