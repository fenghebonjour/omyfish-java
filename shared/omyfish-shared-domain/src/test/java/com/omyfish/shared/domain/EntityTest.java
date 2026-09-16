package com.omyfish.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityTest {

    private static class Fish extends Entity<UUID> {
        Fish(UUID id) { super(id); }
    }

    private static class Bait extends Entity<UUID> {
        Bait(UUID id) { super(id); }
    }

    @Test
    void nullIdIsRejected() {
        assertThatThrownBy(() -> new Fish(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Entity ID must not be null");
    }

    @Test
    void identityIsBasedOnId() {
        UUID id = UUID.randomUUID();
        Fish one = new Fish(id);
        Fish same = new Fish(id);

        assertThat(one.getId()).isEqualTo(id);
        assertThat(one).isEqualTo(same).isEqualTo(one);
        assertThat(one).hasSameHashCodeAs(same);
    }

    @Test
    void differentIdsAreNotEqual() {
        assertThat(new Fish(UUID.randomUUID())).isNotEqualTo(new Fish(UUID.randomUUID()));
    }

    @Test
    void differentTypesWithSameIdAreNotEqual() {
        UUID id = UUID.randomUUID();
        assertThat(new Fish(id)).isNotEqualTo(new Bait(id));
    }

    @Test
    void notEqualToNull() {
        assertThat(new Fish(UUID.randomUUID())).isNotEqualTo(null);
    }
}
