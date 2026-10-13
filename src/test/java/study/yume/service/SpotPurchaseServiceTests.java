package study.yume.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import study.yume.dto.spotpurchase.request.PurchaseUpdateRequest;
import study.yume.model.SpotPurchase;
import study.yume.model.enums.PurchaseKind;
import study.yume.repository.SpotPurchaseRepository;
import study.yume.repository.SpotUserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpotPurchaseServiceTests {
    @Mock SpotPurchaseRepository purchases;
    @Mock SpotUserRepository spots;
    @Mock GoshuinService goshuin;

    private SpotPurchase record() {
        SpotPurchase record = new SpotPurchase();
        record.setId(10L);
        record.setUserId(1L);
        record.setKind(PurchaseKind.GOSHUIN);
        record.setNote("고슈인 화면에서 저장한 최신 메모");
        record.setRecordVersion(3L);
        when(purchases.findByUserIdAndId(1L, 10L)).thenReturn(Optional.of(record));
        return record;
    }

    private PurchaseUpdateRequest input(Long version) {
        return new PurchaseUpdateRequest(null, null, null, null, null, null, null, null, null, "장부 메모", version);
    }

    @Test void staleLedgerCannotOverwriteLatestGoshuin() {
        SpotPurchase record = record();
        assertThatThrownBy(() -> new SpotPurchaseService(purchases, spots, goshuin).updatePurchase(1L, 10L, input(2L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        assertThat(record.getNote()).isEqualTo("고슈인 화면에서 저장한 최신 메모");
        verify(purchases, never()).save(any());
    }

    @Test void missingVersionCannotBypassGoshuinConflictCheck() {
        record();
        assertThatThrownBy(() -> new SpotPurchaseService(purchases, spots, goshuin).updatePurchase(1L, 10L, input(null)))
                .isInstanceOf(ResponseStatusException.class);
        verify(purchases, never()).save(any());
    }

    @Test void currentLedgerVersionCanSaveAndAdvancesVersion() {
        SpotPurchase record = record();
        when(purchases.save(record)).thenReturn(record);
        var saved = new SpotPurchaseService(purchases, spots, goshuin).updatePurchase(1L, 10L, input(3L));
        assertThat(saved.note()).isEqualTo("장부 메모");
        assertThat(saved.recordVersion()).isEqualTo(4L);
    }
}
