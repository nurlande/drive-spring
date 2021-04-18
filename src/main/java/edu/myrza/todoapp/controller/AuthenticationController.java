package edu.myrza.todoapp.controller;

import edu.myrza.todoapp.exceptions.BussinesException;
import edu.myrza.todoapp.model.dto.auth.LoginRequest;
import edu.myrza.todoapp.model.dto.auth.LoginResponse;
import edu.myrza.todoapp.model.dto.auth.RegistrationRequest;
import edu.myrza.todoapp.model.dto.auth.RegistrationResponse;
import edu.myrza.todoapp.model.dto.files.FileRecordDto;
import edu.myrza.todoapp.model.entity.User;
import edu.myrza.todoapp.service.FileService;
import edu.myrza.todoapp.service.UserService;
import edu.myrza.todoapp.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final FileService fileService;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthenticationController(
            AuthenticationManager authenticationManager,
            UserService userService,
            FileService fileService,
            JwtUtil jwtUtil)
    {
        this.fileService = fileService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @CrossOrigin("*")
    @PostMapping("/login")
    public ResponseEntity<?> createAuthenticationToken(@RequestBody LoginRequest request) {

        String username = request.getUsername();
        String password = request.getPassword();

        try{
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException ex) {
            throw new BussinesException(BussinesException.Code.AUTH_001);
        }

        User user = userService.loadUserByUsername(username);

        String token = jwtUtil.generateToken(user);

        return ResponseEntity.ok(new LoginResponse(username, user.getEmail(), token, username));
    }

    @CrossOrigin("*")
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {

        //Here we should save token into 'black list' table
        return ResponseEntity.ok().build();
    }

    @CrossOrigin("*")
    @PostMapping("/register")
    public ResponseEntity<?> registerNewUser(@RequestBody RegistrationRequest req) {
        return ResponseEntity.ok(userService.registerUser(req));
    }
}
