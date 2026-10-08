/**
 * REST API 占位包。
 *
 * <p>阶段一不做 API：网页版（web/ + Thymeleaf）先跑通。等真实业务验证了接口需求后，
 * 再在此包下新增 {@code XxxApiController}（{@code @RestController}），复用 service 层，
 * 返回 {@link com.medislot.dto.ApiResponse}。
 */
package com.medislot.api;
