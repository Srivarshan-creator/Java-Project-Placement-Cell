package com.placement.controller;

import com.placement.model.Role;
import com.placement.model.User;
import com.placement.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RouteSecuritySmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Public routes accessible anonymously")
    void testPublicRoutes() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk());
        mockMvc.perform(get("/register")).andExpect(status().isOk());
        mockMvc.perform(get("/register/company")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unauthenticated request to protected routes redirects to login")
    void testUnauthenticatedRedirect() throws Exception {
        mockMvc.perform(get("/student/dashboard")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/company/dashboard")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "student@college.edu", roles = {"STUDENT"})
    @DisplayName("Student cannot access Admin or Company workspaces")
    void testStudentForbiddenAreas() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/companies")).andExpect(status().isForbidden());
        mockMvc.perform(get("/company/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "company@test.com", roles = {"COMPANY"})
    @DisplayName("Company cannot access Admin or Student workspaces")
    void testCompanyForbiddenAreas() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(get("/student/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@college.edu", roles = {"PLACEMENT_CELL"})
    @DisplayName("Admin cannot access Student or Company specific views")
    void testAdminForbiddenAreas() throws Exception {
        mockMvc.perform(get("/student/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(get("/company/dashboard")).andExpect(status().isForbidden());
    }
}

