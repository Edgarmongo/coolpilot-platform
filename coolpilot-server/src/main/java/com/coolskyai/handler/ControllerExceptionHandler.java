package com.coolskyai.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.coolskyai.exception.BadRequestException;
import com.coolskyai.exception.NotFoundException;
import com.coolskyai.exception.PersistenceException;
import com.coolskyai.model.vo.Result;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @Description: 对Controller层全局异常处理
 * @RestControllerAdvice 捕获异常后，返回json数据类型
 * @Author: CoolSkyAI
 * @Date: 2020-08-14
 */
@RestControllerAdvice
public class ControllerExceptionHandler {
	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	/**
	 * 捕获自定义的404异常
	 *
	 * @param request 请求
	 * @param e       自定义抛出的异常信息
	 * @return
	 */
	@ExceptionHandler(NotFoundException.class)
	public Result notFoundExceptionHandler(HttpServletRequest request, NotFoundException e) {
		logger.error("Request URL : {}, Exception :", request.getRequestURL(), e);
		return Result.create(404, e.getMessage());
	}

	/**
	 * 捕获自定义的持久化异常
	 *
	 * @param request 请求
	 * @param e       自定义抛出的异常信息
	 * @return
	 */
	@ExceptionHandler(PersistenceException.class)
	public Result persistenceExceptionHandler(HttpServletRequest request, PersistenceException e) {
		logger.error("Request URL : {}, Exception :", request.getRequestURL(), e);
		return Result.create(500, e.getMessage());
	}

	/**
	 * 捕获自定义的非法请求异常（参数校验不通过等）
	 *
	 * @param request 请求
	 * @param e       自定义抛出的异常信息
	 * @return
	 */
	@ExceptionHandler(BadRequestException.class)
	public Result badRequestExceptionHandler(HttpServletRequest request, BadRequestException e) {
		logger.warn("Request URL : {}, BadRequest : {}", request.getRequestURL(), e.getMessage());
		return Result.create(400, e.getMessage());
	}

	/**
	 * 捕获自定义的登录失败异常
	 *
	 * @param request 请求
	 * @param e       自定义抛出的异常信息
	 * @return
	 */
	@ExceptionHandler(UsernameNotFoundException.class)
	public Result usernameNotFoundExceptionHandler(HttpServletRequest request, UsernameNotFoundException e) {
		logger.error("Request URL : {}, Exception :", request.getRequestURL(), e);
		return Result.create(401, "用户名或密码错误！");
	}

	/**
	 * 参数校验失败（@Valid / @Validated）
	 *
	 * 这些异常此前一路掉到 Exception 兜底，前端拿到的是 500「异常错误」，
	 * 既误导用户、也把「请求有问题」记成了「服务端有问题」。
	 */
	@ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
	public Result bindExceptionHandler(HttpServletRequest request, Exception e) {
		String message = "参数有误";
		if (e instanceof MethodArgumentNotValidException) {
			FieldError fieldError = ((MethodArgumentNotValidException) e).getBindingResult().getFieldError();
			if (fieldError != null && fieldError.getDefaultMessage() != null) {
				message = fieldError.getDefaultMessage();
			}
		} else if (e instanceof BindException) {
			FieldError fieldError = ((BindException) e).getBindingResult().getFieldError();
			if (fieldError != null && fieldError.getDefaultMessage() != null) {
				message = fieldError.getDefaultMessage();
			}
		}
		logger.warn("Request URL : {}, 参数校验失败 : {}", request.getRequestURL(), message);
		return Result.create(400, message);
	}

	/**
	 * 缺参数、参数类型不对、请求体无法解析 —— 都是客户端的问题，返回 400
	 */
	@ExceptionHandler({MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class})
	public Result badRequestParamHandler(HttpServletRequest request, Exception e) {
		logger.warn("Request URL : {}, 请求参数异常 : {}", request.getRequestURL(), e.getMessage());
		if (e instanceof MissingServletRequestParameterException) {
			return Result.create(400, "缺少必要参数：" + ((MissingServletRequestParameterException) e).getParameterName());
		}
		if (e instanceof MethodArgumentTypeMismatchException) {
			return Result.create(400, "参数格式不正确：" + ((MethodArgumentTypeMismatchException) e).getName());
		}
		return Result.create(400, "请求体格式不正确");
	}

	/**
	 * 静态资源不存在（/image/** 等）
	 *
	 * 不处理的话会落到 Exception 兜底变成「500 异常错误」，
	 * 把「文件没了」误报成「服务端出错」。
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public Result noResourceFoundHandler(HttpServletRequest request, NoResourceFoundException e) {
		logger.warn("Request URL : {}, 资源不存在 : {}", request.getRequestURL(), e.getResourcePath());
		return Result.create(404, "资源不存在");
	}

	/**
	 * 捕获其它异常
	 *
	 * @param request 请求
	 * @param e       异常信息
	 * @return
	 */
	@ExceptionHandler(Exception.class)
	public Result exceptionHandler(HttpServletRequest request, Exception e) {
		logger.error("Request URL : {}, Exception :", request.getRequestURL(), e);
		return Result.create(500, "异常错误");
	}
}
