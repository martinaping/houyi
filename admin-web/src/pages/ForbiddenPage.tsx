import { Button, Result } from 'antd';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { firstAllowedPath } from '../auth/permissions';

export function ForbiddenPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { user } = useAuth();
  return (
    <Result
      status="403"
      title="403"
      subTitle={t('common.forbiddenHint')}
      extra={
        <Button type="primary" onClick={() => navigate(firstAllowedPath(user?.permissions ?? []))}>
          {t('common.backAllowed')}
        </Button>
      }
    />
  );
}
