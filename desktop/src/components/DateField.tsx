import { useEffect, useRef } from 'react';
import { Datepicker } from 'material/react';
import type { DatepickerElement } from 'material/elements';

interface DateFieldProps {
  value: string;
  onValueChange: (value: string) => void;
  disabled: boolean;
}

const months = ['January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'];
const weekdays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const labels: Record<string, string> = {
  'Enter a valid date (YYYY-MM-DD).': '请输入有效日期（YYYY-MM-DD）。',
  'Enter a selectable date (YYYY-MM-DD).': '请输入可选择的日期（YYYY-MM-DD）。',
  'Date is not selectable.': '该日期不可选择。',
  'Select a date.': '请选择日期。',
  'OK': '确定', 'Cancel': '取消', 'Date': '日期', 'Select date': '选择日期',
  'Select month': '选择月份', 'Select year': '选择年份',
  'Choose month': '选择月份', 'Choose year': '选择年份',
  'Previous month': '上个月', 'Next month': '下个月',
  'Previous year': '上一年', 'Next year': '下一年',
  'Switch to calendar': '切换到日历', 'Switch to date input': '切换到日期输入',
  'Use arrow keys for days, Home and End for the week, Page Up and Page Down for months, with Shift for years.':
    '方向键切换日期，Home 和 End 移至周首尾，Page Up 和 Page Down 切换月份，配合 Shift 切换年份。',
};
months.forEach((month, index) => {
  labels[month] = `${index + 1}月`;
  labels[month.slice(0, 3)] = `${index + 1}月`;
});
weekdays.forEach((day, index) => { labels[day] = `星期${'日一二三四五六'[index]}`; });

/** material 3.2.4 has no locale option. Translate presentation only;
 * all date validation, selection, navigation and focus remain upstream. */
function localize(root: ShadowRoot) {
  const translate = (text: string) => {
    if (labels[text]) return labels[text];
    const headline = text.match(/^([A-Za-z]+) (\d{1,2}), (\d{4})$/);
    if (headline && labels[headline[1]]) return `${headline[3]}年${labels[headline[1]]}${headline[2]}日`;
    const monthYear = text.match(/^([A-Za-z]+) (\d{4})$/);
    if (monthYear && labels[monthYear[1]]) return `${monthYear[2]}年${labels[monthYear[1]]}`;
    return text;
  };
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) {
    const node = walker.currentNode;
    if (node.parentElement?.closest('style, script')) continue;
    const translated = translate(node.textContent ?? '');
    if (translated !== node.textContent) node.textContent = translated;
  }
  root.querySelectorAll<HTMLElement>('[aria-label]').forEach((element) => {
    const label = element.getAttribute('aria-label') ?? '';
    const date = element.dataset.date;
    const translated = date
      ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'full' }).format(new Date(`${date}T12:00:00`))
      : translate(label);
    if (label !== translated) element.setAttribute('aria-label', translated);
  });
  root.querySelectorAll<HTMLElement>('.mtrl-datepicker__weekday').forEach((element, index) => {
    const text = '日一二三四五六'[index % 7];
    if (element.textContent !== text) element.textContent = text;
  });
}

export function DateField({ value, onValueChange, disabled }: DateFieldProps) {
  const ref = useRef<DatepickerElement>(null);
  useEffect(() => {
    const root = ref.current?.shadowRoot;
    if (!root) return;
    const observer = new MutationObserver(() => localize(root));
    observer.observe(root, { childList: true, characterData: true, subtree: true });
    localize(root);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const host = ref.current;
    if (!host) return;
    const calendar = () => host.shadowRoot?.querySelector('dialog');
    const position = () => {
      const panel = calendar();
      if (!panel?.open) return;
      const bounds = host.getBoundingClientRect();
      // Keep the docked panel inside the scrolling workbench, above the task bar.
      const bottom = host.closest('.page')?.getBoundingClientRect().bottom ?? window.innerHeight;
      host.style.setProperty('--app-date-height', `${Math.max(0, bottom - bounds.bottom - 24)}px`);
    };
    host.addEventListener('open', position);
    window.addEventListener('resize', position);
    document.addEventListener('scroll', position, true);
    return () => {
      host.removeEventListener('open', position);
      window.removeEventListener('resize', position);
      document.removeEventListener('scroll', position, true);
    };
  }, []);

  return (
    <Datepicker
      ref={ref}
      className="md3-date-field"
      label="结束日期"
      variant="docked"
      closeOnSelect
      dateFormat="YYYY-MM-DD"
      supportingText="YYYY-MM-DD"
      value={value}
      disabled={disabled}
      required
      onChange={(event) => onValueChange(event.detail.value)}
    />
  );
}
