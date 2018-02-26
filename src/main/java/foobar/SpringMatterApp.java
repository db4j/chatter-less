package foobar;

import com.google.gson.Gson;
import foobar.SpringMatterAuth.MyAuth;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Function;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.filter.OrderedHiddenHttpMethodFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
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
@PropertySource("classpath:application.properties")
public class SpringMatterApp {
    static final Log logger = LogFactory.getLog(SpringMatterApp.class);

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
                    .csrf().disable()
                    .addFilterBefore(new AuthenticationFilter(), BasicAuthenticationFilter.class)
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.NEVER);
        }
    }

    public static class CatchFilter extends GenericFilterBean {
        public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)
                throws IOException,ServletException {
            // jetty throws ISE if a request or upload is too large, catch them here and 413
            // fixme - we catch all exceptions but maybe we shouldn't ???
            // some jetty verbage still shows up in the log - no harm, no foul
            //   https://github.com/dCache/dcache/issues/2314
            try {
                chain.doFilter(request,response);
            }
            catch (Exception ise) {
                int status = ise instanceof IllegalStateException ? 413:400;
                ((HttpServletResponse) response)
                        .setStatus(status);
                if (status==400)
                    logger.warn("suppressed exception: " + ise);                    
            }
        }
    }

    @Bean
    public FilterRegistrationBean regCatch() {
        FilterRegistrationBean reg = new FilterRegistrationBean();
        reg.setFilter(new CatchFilter());
        reg.setOrder(OrderedHiddenHttpMethodFilter.DEFAULT_ORDER-1);
        return reg;
    }


    public static void doMain(MatterControl matter,int port) throws Exception {
        HashMap<String, Object> props = new HashMap<>();
        props.put("server.port", port);

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

        new SpringApplicationBuilder()
            .sources(SpringMatterApp.class)                
            .properties(props)
            .listeners(lis)
            .run();
        System.err.println("startup complete");
    }
}

