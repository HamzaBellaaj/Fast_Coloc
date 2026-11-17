package tn.fastcoloc.auth;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded local throttle; Supabase's own limits still apply. Does not trust X-Forwarded-For. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
 private record Window(long start,int count){}
 private final ConcurrentHashMap<String,Window> attempts=new ConcurrentHashMap<>();
 protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  if(!request.getRequestURI().startsWith("/api/v1/auth/")||!request.getMethod().equals("POST")){chain.doFilter(request,response);return;}
  long now=System.currentTimeMillis();String address=request.getRemoteAddr();
  if(attempts.size()>10000)attempts.entrySet().removeIf(e->now-e.getValue().start()>600000);
  if(attempts.size()>10000&&!attempts.containsKey(address)){reject(response);return;}
  Window window=attempts.compute(address,(key,old)->old==null||now-old.start()>600000?new Window(now,1):new Window(old.start(),old.count()+1));
  if(window.count()>100){reject(response);return;}chain.doFilter(request,response);
 }
 private void reject(HttpServletResponse r)throws IOException{r.setStatus(429);r.setHeader("Retry-After","600");r.setContentType("application/json");r.getWriter().write("{\"status\":429,\"code\":\"AUTH_RATE_LIMITED\",\"message\":\"Trop de tentatives.\"}");}
}
