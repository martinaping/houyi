// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from 'vitest';
import { consumeSessionExpired, notifyUnauthorized } from './client';

describe('会话失效提示标记', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('notifyUnauthorized 写入标记，consumeSessionExpired 读取并清除', () => {
    notifyUnauthorized();

    expect(sessionStorage.getItem('houyi:session-expired')).toBe('1');
    expect(consumeSessionExpired()).toBe(true);
    expect(consumeSessionExpired()).toBe(false);
  });

  it('无标记时返回 false', () => {
    expect(consumeSessionExpired()).toBe(false);
  });
});
