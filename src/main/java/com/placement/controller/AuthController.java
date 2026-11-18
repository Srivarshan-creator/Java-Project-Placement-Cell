package com.placement.controller;

import com.placement.dto.CompanyRegistrationDto;
import com.placement.dto.StudentRegistrationDto;
import com.placement.exception.BadRequestException;
import com.placement.security.CustomUserDetails;
import com.placement.service.CompanyService;
import com.placement.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final StudentService studentService;
    private final CompanyService companyService;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null) {
            return switch (userDetails.getRole()) {
                case STUDENT -> "redirect:/student/dashboard";
                case COMPANY -> "redirect:/company/dashboard";
                case PLACEMENT_CELL -> "redirect:/admin/dashboard";
            };
        }
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                            @RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {
        if (userDetails != null) {
            return "redirect:/";
        }
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid email or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "You have been logged out successfully.");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (userDetails != null) {
            return "redirect:/";
        }
        if (!model.containsAttribute("studentDto")) {
            model.addAttribute("studentDto", new StudentRegistrationDto());
        }
        model.addAttribute("branches", new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
        return "auth/register";
    }

    @PostMapping("/register")
    public String handleRegister(@Valid @ModelAttribute("studentDto") StudentRegistrationDto dto,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("branches", new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
            return "auth/register";
        }

        try {
            studentService.registerStudent(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Registration successful! You can now log in with your credentials.");
            return "redirect:/login";
        } catch (BadRequestException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("branches", new String[]{"CSE", "IT", "ECE", "MECH", "CIVIL", "EEE"});
            return "auth/register";
        }
    }

    @GetMapping("/register/company")
    public String registerCompanyPage(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (userDetails != null) {
            return "redirect:/";
        }
        if (!model.containsAttribute("companyDto")) {
            model.addAttribute("companyDto", new CompanyRegistrationDto());
        }
        return "auth/register-company";
    }

    @PostMapping("/register/company")
    public String handleRegisterCompany(@Valid @ModelAttribute("companyDto") CompanyRegistrationDto dto,
                                        BindingResult bindingResult,
                                        RedirectAttributes redirectAttributes,
                                        Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/register-company";
        }

        try {
            companyService.registerCompany(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Company registration successful! You can now log in with your credentials.");
            return "redirect:/login";
        } catch (BadRequestException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register-company";
        }
    }
}

