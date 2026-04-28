# Sprint 1: Project Setup + Auth

**Goal:** Running backend + frontend with working JWT auth.
**Duration:** Week 1-2 (~20-30 hrs total)

---

## Backend

### Task 1: Project Init
**Files:** Create `backend/`

1. Go to [start.spring.io](https://start.spring.io), add: Web, JPA, Security, PostgreSQL Driver, Lombok, Flyway
2. Download, unzip into `backend/`
3. Commit: `git commit -m "chore: init Spring Boot project"`

---

### Task 2: Database Config
**Files:** `backend/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecommerce_db
    username: ${DB_USER}
    password: ${DB_PASS}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

app:
  jwt-secret: ${JWT_SECRET}
  jwt-expiration-ms: 86400000
```

Create `.env` (never commit):
```
DB_USER=postgres
DB_PASS=postgres
JWT_SECRET=change-me-32-chars-minimum-secret
```

Commit: `chore: add application config`

---

### Task 3: Users Migration
**Files:** Create `backend/src/main/resources/db/migration/V1__create_users.sql`

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);
```

Run: `./mvnw flyway:migrate`
Expected: Migration applied successfully

Commit: `db: add users table migration`

---

### Task 4: User Entity + Repository
**Files:**
- Create `src/main/java/com/ecommerce/entity/User.java`
- Create `src/main/java/com/ecommerce/repository/UserRepository.java`

```java
// User.java
@Entity @Table(name = "users")
@Data @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String passwordHash;
    private String name;
    private LocalDateTime createdAt;
}
```

```java
// UserRepository.java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

Commit: `feat: add User entity and repository`

---

### Task 5: Auth DTOs
**Files:** Create `src/main/java/com/ecommerce/dto/`

```java
// RegisterRequest.java
@Data public class RegisterRequest {
    @NotBlank @Email private String email;
    @NotBlank @Size(min = 6) private String password;
    @NotBlank private String name;
}

// LoginRequest.java
@Data public class LoginRequest {
    @NotBlank @Email private String email;
    @NotBlank private String password;
}

// AuthResponse.java
@Data @AllArgsConstructor
public class AuthResponse {
    private String token;
    private String email;
    private String name;
}
```

Commit: `feat: add auth DTOs`

---

### Task 6: JwtUtil
**Files:** Create `src/main/java/com/ecommerce/security/JwtUtil.java`

Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
```

```java
@Component
public class JwtUtil {
    @Value("${app.jwt-secret}") private String secret;
    @Value("${app.jwt-expiration-ms}") private long expirationMs;

    public String generateToken(String email) {
        return Jwts.builder()
            .subject(email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
            .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
            .build().parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean isValid(String token) {
        try { extractEmail(token); return true; }
        catch (Exception e) { return false; }
    }
}
```

Commit: `feat: add JWT utility`

---

### Task 7: AuthService
**Files:** Create `src/main/java/com/ecommerce/service/AuthService.java`

```java
@Service @RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setName(req.getName());
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName());
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        return new AuthResponse(jwtUtil.generateToken(user.getEmail()), user.getEmail(), user.getName());
    }
}
```

Commit: `feat: add AuthService`

---

### Task 8: SecurityConfig + AuthController
**Files:**
- Create `src/main/java/com/ecommerce/config/SecurityConfig.java`
- Create `src/main/java/com/ecommerce/controller/AuthController.java`

```java
// SecurityConfig.java
@Configuration @EnableWebSecurity @RequiredArgsConstructor
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/auth/**").permitAll()
                .anyRequest().authenticated())
            .build();
    }

    @Bean public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

```java
// AuthController.java
@RestController @RequestMapping("/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(201).body(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
```

Commit: `feat: add SecurityConfig and AuthController`

---

### Task 9: Auth Tests
**Files:** Create `src/test/java/com/ecommerce/AuthControllerTest.java`

```java
@SpringBootTest @AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test void register_success() throws Exception {
        mockMvc.perform(post("/auth/register")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new RegisterRequest("test@email.com", "password123", "Test User"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test void login_invalidPassword_returns401() throws Exception {
        mockMvc.perform(post("/auth/login")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new LoginRequest("test@email.com", "wrongpassword"))))
            .andExpect(status().isUnauthorized());
    }
}
```

Run: `./mvnw test`
Expected: All tests pass

Commit: `test: add auth controller tests`

---

## Frontend

### Task 10: React Init
**Files:** Create `frontend/`

```bash
npm create vite@latest frontend -- --template react
cd frontend
npm install
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
npm install @reduxjs/toolkit react-redux axios react-router-dom
```

Commit: `chore: init React project with Vite + Tailwind`

---

### Task 11: Redux Auth Slice
**Files:**
- Create `frontend/src/store/store.js`
- Create `frontend/src/store/authSlice.js`

```js
// authSlice.js
import { createSlice } from '@reduxjs/toolkit'

const authSlice = createSlice({
  name: 'auth',
  initialState: {
    token: localStorage.getItem('token') || null,
    user: JSON.parse(localStorage.getItem('user')) || null,
  },
  reducers: {
    setCredentials: (state, { payload }) => {
      state.token = payload.token
      state.user = { email: payload.email, name: payload.name }
      localStorage.setItem('token', payload.token)
      localStorage.setItem('user', JSON.stringify(state.user))
    },
    logout: (state) => {
      state.token = null
      state.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    },
  },
})

export const { setCredentials, logout } = authSlice.actions
export default authSlice.reducer
```

```js
// store.js
import { configureStore } from '@reduxjs/toolkit'
import authReducer from './authSlice'
export const store = configureStore({ reducer: { auth: authReducer } })
```

Commit: `feat: add Redux store and authSlice`

---

### Task 12: Axios Instance
**Files:** Create `frontend/src/api/axios.js`

```js
import axios from 'axios'
import { store } from '../store/store'

const api = axios.create({ baseURL: import.meta.env.VITE_API_URL })

api.interceptors.request.use((config) => {
  const token = store.getState().auth.token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export default api
```

Create `frontend/.env`:
```
VITE_API_URL=http://localhost:8080
```

Commit: `feat: add Axios instance with auth interceptor`

---

### Task 13: Login + Signup Pages
**Files:**
- Create `frontend/src/pages/LoginPage.jsx`
- Create `frontend/src/pages/SignupPage.jsx`

```jsx
// LoginPage.jsx
import { useState } from 'react'
import { useDispatch } from 'react-redux'
import { useNavigate } from 'react-router-dom'
import { setCredentials } from '../store/authSlice'
import api from '../api/axios'

export default function LoginPage() {
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const dispatch = useDispatch()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    try {
      const { data } = await api.post('/auth/login', form)
      dispatch(setCredentials(data))
      navigate('/')
    } catch {
      setError('Invalid email or password')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4 w-80">
        <h1 className="text-2xl font-bold">Login</h1>
        {error && <p className="text-red-500">{error}</p>}
        <input className="border p-2 rounded" placeholder="Email"
          value={form.email} onChange={e => setForm({...form, email: e.target.value})} />
        <input className="border p-2 rounded" placeholder="Password" type="password"
          value={form.password} onChange={e => setForm({...form, password: e.target.value})} />
        <button className="bg-blue-600 text-white p-2 rounded" type="submit">Login</button>
      </form>
    </div>
  )
}
```

*(SignupPage.jsx follows same pattern, hitting `/auth/register`)*

Commit: `feat: add Login and Signup pages`

---

### Task 14: Router + Protected Route
**Files:**
- Create `frontend/src/components/ProtectedRoute.jsx`
- Modify `frontend/src/App.jsx`

```jsx
// ProtectedRoute.jsx
import { useSelector } from 'react-redux'
import { Navigate } from 'react-router-dom'

export default function ProtectedRoute({ children }) {
  const token = useSelector(s => s.auth.token)
  return token ? children : <Navigate to="/login" replace />
}
```

```jsx
// App.jsx
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import ProtectedRoute from './components/ProtectedRoute'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/" element={<ProtectedRoute><div>Home (coming Sprint 2)</div></ProtectedRoute>} />
      </Routes>
    </BrowserRouter>
  )
}
```

Commit: `feat: add routing and protected route`

---

## Definition of Done

- [ ] `POST /auth/register` returns 201 + JWT token
- [ ] `POST /auth/login` returns 200 + JWT token
- [ ] Wrong password returns 401
- [ ] React app: Login form hits backend, stores token, redirects
- [ ] React app: Signup form registers user
- [ ] Protected route redirects unauthenticated users
- [ ] All backend tests pass (`./mvnw test`)
- [ ] No secrets committed to git

---

## Pitfalls to Avoid

- Never return the password hash in any response
- Always use `@Valid` on controller request bodies or validation silently skips
- JWT secret must be at least 32 chars for HMAC-SHA256
- CORS: add `@CrossOrigin` or a `CorsFilter` bean or React can't hit the API locally
