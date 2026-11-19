import dayjs from 'dayjs';

const TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss';

export function formatTime(value: number | null | undefined): string {
  if (value == null || value <= 0) {
    return '-';
  }
  return dayjs(value).format(TIME_FORMAT);
}

export function formatNumber(value: number | null | undefined): string {
  if (value == null) {
    return '-';
  }
  return value.toLocaleString();
}
