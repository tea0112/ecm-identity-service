package com.ecm.security.identity.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.ecm.security.identity.shared.RoleLookup;
import com.ecm.security.identity.user.controller.dto.CreateUserRequest;
import com.ecm.security.identity.user.controller.dto.UpdateUserRequest;
import com.ecm.security.identity.user.domain.User;
import com.ecm.security.identity.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false) // Bypass security filters for logic testing
@SuppressWarnings("deprecation")
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private RoleLookup roleLookup;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listUsers_ShouldReturnList() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("user1")
                .build();
        given(userService.getAllUsers()).willReturn(List.of(user));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("user1"));
    }

    @Test
    void getUserById_Found_ShouldReturnUser() throws Exception {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).username("user1").build();
        given(userService.getUserById(id)).willReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getUserById_NotFound_ShouldReturn404() throws Exception {
        UUID id = UUID.randomUUID();
        given(userService.getUserById(id)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void createUser_Valid_ShouldReturnCreated() throws Exception {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("newuser")
                .email("new@example.com")
                .password("Pass123")
                .build();

        User user = User.builder()
                .id(UUID.randomUUID())
                .username("newuser")
                .email("new@example.com")
                .build();

        given(userService.createUser(anyString(), anyString(), anyString(), any(), any()))
                .willReturn(user);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"));
    }

    @Test
    void updateUser_ShouldReturnUpdated() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("NewName")
                .build();

        User user = User.builder().id(id).firstName("NewName").build();
        given(userService.updateUser(any(UUID.class), any(), any(), any(), any()))
                .willReturn(user);

        mockMvc.perform(patch("/api/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("NewName"));
    }

    @Test
    void deleteUser_ShouldReturnNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/users/{id}", id))
                .andExpect(status().isNoContent());
    }
}
