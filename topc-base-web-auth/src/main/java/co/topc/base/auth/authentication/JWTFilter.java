package co.topc.base.auth.authentication;

import co.topc.web.commons.constants.TopcStringConstant;
import co.topc.base.auth.common.util.SpringContextUtil;
import co.topc.base.auth.common.util.TopcAuthUtil;
import co.topc.base.auth.properties.AuthProperties;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.web.filter.authc.BasicHttpAuthenticationFilter;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class JWTFilter extends BasicHttpAuthenticationFilter {

    private static Logger logger = LoggerFactory.getLogger(JWTFilter.class);

    private static final String TOKEN = "Authentication";

    private AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) throws UnauthorizedException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        AuthProperties authProperties = SpringContextUtil.getBean(AuthProperties.class);
        String[] anonUrl = StringUtils.splitByWholeSeparatorPreserveAllTokens(authProperties.getShiro().getAnonUrl(), TopcStringConstant.COMMA);

        boolean match = false;
        for (String u : anonUrl) {
            if (pathMatcher.match(u, httpServletRequest.getRequestURI())) {
                match = true;
            }
        }
        if (match) {return true;}
        if (isLoginAttempt(request, response)) {
            return executeLogin(request, response);
        }
        return false;
    }

    @Override
    protected boolean isLoginAttempt(ServletRequest request, ServletResponse response) {
        HttpServletRequest req = (HttpServletRequest) request;
        String token = req.getHeader(TOKEN);
        return token != null;
    }

    @Override
    protected boolean executeLogin(ServletRequest request, ServletResponse response) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String token = httpServletRequest.getHeader(TOKEN);
        JWTToken jwtToken = new JWTToken(TopcAuthUtil.decryptToken(token));
        try {
            getSubject(request, response).login(jwtToken);
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
    }

    /**
     * 对跨域提供支持
     */
    @Override
    protected boolean preHandle(ServletRequest request, ServletResponse response) throws Exception {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        httpServletResponse.setHeader("Access-control-Allow-Origin", httpServletRequest.getHeader("Origin"));
        httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS,PUT,DELETE");
        httpServletResponse.setHeader("Access-Control-Allow-Headers", httpServletRequest.getHeader("Access-Control-Request-Headers"));
        // 跨域时会首先发送一个 option请求，这里我们给 option请求直接返回正常状态
        if (httpServletRequest.getMethod().equals(RequestMethod.OPTIONS.name())) {
            httpServletResponse.setStatus(HttpStatus.OK.value());
            return false;
        }
        return super.preHandle(request, response);
    }

    @Override
    protected boolean sendChallenge(ServletRequest request, ServletResponse response) {
        logger.debug("Authentication required: sending 401 Authentication challenge response.");
        HttpServletResponse httpResponse = WebUtils.toHttp(response);
        httpResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
        httpResponse.setCharacterEncoding("utf-8");
        httpResponse.setContentType("application/json; charset=utf-8");
        final String message = "未认证，请在前端系统进行认证";
        try (PrintWriter out = httpResponse.getWriter()) {
            String responseJson = "{\"message\":\"" + message + "\"}";
            out.print(responseJson);
        } catch (IOException e) {
            logger.error("sendChallenge error：", e);
        }
        return false;
    }
}