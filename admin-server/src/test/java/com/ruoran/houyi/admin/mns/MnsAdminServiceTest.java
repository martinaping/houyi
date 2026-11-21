package com.ruoran.houyi.admin.mns;

import com.ruoran.houyi.admin.error.ApiException;
import com.ruoran.houyi.admin.settings.SettingsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MnsAdminServiceTest {

    @Test
    void overviewReportsDisabledWithoutClient() {
        MnsSettingsService settings = mock(MnsSettingsService.class);
        when(settings.enabled()).thenReturn(false);
        MnsAdminService service = new MnsAdminService(settings, mock(SettingsService.class));

        MnsViews.Overview overview = service.overview(null);

        assertFalse(overview.available());
        assertFalse(overview.enabled());
    }

    @Test
    void rejectsEmptyTestMessage() {
        MnsAdminService service = new MnsAdminService(mock(MnsSettingsService.class),
                mock(SettingsService.class));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.testSend("q", "", 0));

        assertEquals("MNS_MESSAGE_REQUIRED", ex.getCode());
    }

    @Test
    void rejectsInvalidDelay() {
        MnsAdminService service = new MnsAdminService(mock(MnsSettingsService.class),
                mock(SettingsService.class));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.testSend("q", "{}", 604801));

        assertEquals("MNS_DELAY_INVALID", ex.getCode());
    }
}
