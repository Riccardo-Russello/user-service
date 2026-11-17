package it.russello.contocorrente.repository;

import it.russello.contocorrente.entity.Person;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PersonRepositoryTest {
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private RestClient restClient;

    @Test
    void saveReadPersonFromDatabase() {
        Person person = new Person("Mario", "Rossi", LocalDate.of(1990, 5, 15));
        person.setUsername("mario.rossi");
        person.setPassword("passwmario");
        Person saved = personRepository.saveAndFlush(person);
        Long id = saved.getId();
        assertNotNull(id);

        entityManager.clear();
        Person found = personRepository.findById(id).orElseThrow();

        assertEquals(id, found.getId());
        assertEquals("Mario", found.getFirstName());
        assertEquals("Rossi", found.getLastName());
        assertEquals(LocalDate.of(1990, 5, 15), found.getDateOfBirth());
        assertEquals("mario.rossi", found.getUsername());
        assertEquals("passwmario", found.getPassword());

    }
}
