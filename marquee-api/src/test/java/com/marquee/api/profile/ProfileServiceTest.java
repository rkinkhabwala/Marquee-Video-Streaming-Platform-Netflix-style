package com.marquee.api.profile;

import com.marquee.api.recsys.EngagementRecorder;
import com.marquee.api.user.User;
import com.marquee.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EngagementRecorder engagement;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void createProfileRejectsWhenUserHasFiveProfiles() throws Exception {
        User user = new User("user@example.com", "encoded");
        setId(user, 7L);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(profileRepository.countByUserId(7L)).thenReturn(5L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> profileService.createProfile(7L, new CreateProfileRequest("Max", null, false)));

        assertEquals(409, ex.getStatusCode().value());
        assertEquals("Profile limit reached", ex.getReason());
    }

    @Test
    void createProfileCreatesProfileAndReturnsDto() throws Exception {
        User user = new User("user@example.com", "encoded");
        setId(user, 9L);

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(profileRepository.countByUserId(9L)).thenReturn(2L);

        Profile saved = new Profile(user, "Sam", "avatar.png", true);
        setId(saved, 42L);
        when(profileRepository.save(any(Profile.class))).thenReturn(saved);

        ProfileResponse response = profileService.createProfile(9L, new CreateProfileRequest(" Sam ", "avatar.png", true));

        assertEquals(42L, response.id());
        assertEquals("Sam", response.name());
        assertTrue(response.isKids());
        verify(profileRepository).save(any(Profile.class));
    }

    @Test
    void updateProfileTrimsNameAndPreservesValues() throws Exception {
        User user = new User("user@example.com", "encoded");
        setId(user, 11L);

        Profile profile = new Profile(user, "Old", "old.png", false);
        setId(profile, 13L);

        when(profileRepository.findByIdAndUserId(13L, 11L)).thenReturn(Optional.of(profile));
        when(profileRepository.save(profile)).thenReturn(profile);

        ProfileResponse response = profileService.updateProfile(11L, 13L,
                new UpdateProfileRequest("  New Name  ", "new.png", true));

        assertEquals("New Name", response.name());
        assertEquals("new.png", response.avatarKey());
        assertTrue(response.isKids());
    }

    @Test
    void getProfilesForUserMapsToResponses() throws Exception {
        User user = new User("user@example.com", "encoded");
        setId(user, 5L);

        Profile profile = new Profile(user, "Alex", null, false);
        setId(profile, 2L);
        when(profileRepository.findByUserIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(profile));

        List<ProfileResponse> responses = profileService.getProfilesForUser(5L);

        assertEquals(1, responses.size());
        assertEquals("Alex", responses.get(0).name());
    }

    private static void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
