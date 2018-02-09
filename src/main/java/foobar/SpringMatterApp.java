package foobar;

import com.google.gson.Gson;
import foobar.SpringMatterAuth.MyAuth;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Function;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.authentication.BearerTokenExtractor;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.WebUtils;

@SpringBootApplication
public class SpringMatterApp {
    

    static <TT,UU> UU maybe(TT obj,Function<TT,UU> map) { return obj==null ? null:map.apply(obj); }
    
    static BearerTokenExtractor bearer = new BearerTokenExtractor();
    public static class AuthenticationFilter extends GenericFilterBean {
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            String token = maybe(WebUtils.getCookie(req,MatterControl.mmauthtoken),c -> c.getValue());
            String uid = maybe(WebUtils.getCookie(req,MatterControl.mmuserid),c -> c.getValue());
            Authentication bear = bearer.extract(req);
            if (token==null)
                token = (String) bear.getPrincipal();

            Authentication auth = new MyAuth(uid,token);
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        }
    }
    
    @Configuration
    public static class GsonHttpMessageConverterConfiguration {
        @Bean
        public GsonHttpMessageConverter gsonHttpMessageConverter(Gson gson) {
            GsonHttpMessageConverter converter = new GsonHttpMessageConverter();
            converter.setGson(gson);
            return converter;
        }
    }
    
    @Configuration
    public static class SecurityConfiguration extends WebSecurityConfigurerAdapter {
        protected void configure(HttpSecurity http) throws Exception {
            http.authorizeRequests().antMatchers("/").permitAll().and()
                    .addFilterBefore(new AuthenticationFilter(), BasicAuthenticationFilter.class)
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.NEVER);
        }
    }
    public static void doMain(MatterControl matter) throws Exception {
        SpringApplication app = new SpringApplication(SpringMatterApp.class);
        ApplicationListener<ApplicationContextEvent> lis = new ApplicationListener() {
            public void onApplicationEvent(ApplicationEvent event) {
                if (event instanceof ContextRefreshedEvent) {
                    ((ContextRefreshedEvent) event)
                            .getApplicationContext()
                            .getBean(SpringMatter.class)
                            .setup(matter);
                }
            }
        };
        app.setListeners(Arrays.asList(lis));
        app.run();
        System.err.println("startup complete");
    }
}
