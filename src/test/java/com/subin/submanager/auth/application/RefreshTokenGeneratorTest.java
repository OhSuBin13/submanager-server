package com.subin.submanager.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenGeneratorTest {

    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();

    @Test
    void 토큰은_43자_URL_안전_문자열이고_매번_다르다() {
        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 해시는_16진수_64자이고_같은_입력에_같은_값이다() {
        String token = generator.generate();

        assertThat(generator.hash(token)).hasSize(64).matches("[0-9a-f]+");
        assertThat(generator.hash(token)).isEqualTo(generator.hash(token));
        assertThat(generator.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
