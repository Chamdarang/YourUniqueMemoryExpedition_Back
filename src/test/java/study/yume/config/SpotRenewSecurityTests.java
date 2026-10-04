package study.yume.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import study.yume.controller.SpotController;
import study.yume.security.CustomUserDetailsService;
import study.yume.security.JwtUtil;
import study.yume.service.SpotService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpotController.class)
@Import(SecurityConfig.class)
class SpotRenewSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean SpotService spots;
    @MockitoBean JwtUtil jwt;
    @MockitoBean CustomUserDetailsService users;
    @MockitoBean org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void rejectsAnonymousRenewal() throws Exception {
        mvc.perform(post("/api/spots/test/renew/place-1")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(spots);
    }

    @Test
    void rejectsRegularUserRenewal() throws Exception {
        mvc.perform(post("/api/spots/test/renew/place-1").with(user("user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(spots);
    }

    @Test
    void allowsAdminRenewal() throws Exception {
        mvc.perform(post("/api/spots/test/renew/place-1").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(spots).spotDataUpdate(eq("place-1"), any());
    }
}
