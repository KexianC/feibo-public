package com.hzmwmt.common.context;

/**
 * 当前请求登录状态上下文。
 */
public class LoginContextHolder {

    private static final ThreadLocal<LoginUser> LOGIN_USER = new ThreadLocal<>();

    private LoginContextHolder() {
    }

    /**
     * 设置当前请求登录状态。
     *
     * @param loggedIn 是否已登录
     */
    public static void setLoggedIn(boolean loggedIn) {
        LOGIN_USER.set(new LoginUser(null, null, null, null, loggedIn));
    }

    public static void setUser(Long userId, String username, String displayName, String role, boolean loggedIn) {
        LOGIN_USER.set(new LoginUser(userId, username, displayName, role, loggedIn));
    }

    /**
     * 获取当前请求登录状态。
     *
     * @return 是否已登录
     */
    public static boolean isLoggedIn() {
        LoginUser user = LOGIN_USER.get();
        return user != null && user.loggedIn();
    }

    public static boolean isAdmin() {
        LoginUser user = LOGIN_USER.get();
        return user != null && user.loggedIn() && "ADMIN".equalsIgnoreCase(user.role());
    }

    public static LoginUser currentUser() {
        return LOGIN_USER.get();
    }

    /**
     * 清理当前请求登录状态。
     */
    public static void clear() {
        LOGIN_USER.remove();
    }

    public record LoginUser(Long userId, String username, String displayName, String role, boolean loggedIn) {
    }
}
