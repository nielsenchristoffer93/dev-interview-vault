package com.vault.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyTreeVerificationTest {

    @Test
    @DisplayName("The Fix: Bekräfta att modern Jackson-version med findAndRegisterModules() laddats korrekt utan NoSuchMethodError")
    void testTheFix_modernJacksonMethodExists() throws Exception {
        // Om äldre version laddats via felaktig transitiv medling skulle denna metod saknas
        Method findAndRegisterModulesMethod = ObjectMapper.class.getMethod("findAndRegisterModules");

        assertThat(findAndRegisterModulesMethod).isNotNull();

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        // Verifiera att versionen är 2.10+
        assertThat(mapper.version().getMajorVersion()).isEqualTo(2);
        assertThat(mapper.version().getMinorVersion()).isGreaterThanOrEqualTo(10);
    }
}
