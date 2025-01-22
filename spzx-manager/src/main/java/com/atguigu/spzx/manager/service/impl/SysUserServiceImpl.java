package com.atguigu.spzx.manager.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.atguigu.spzx.common.exception.GuiguException;
import com.atguigu.spzx.manager.mapper.SysUserMapper;
import com.atguigu.spzx.manager.service.SysUserService;
import com.atguigu.spzx.model.dto.system.LoginDto;
import com.atguigu.spzx.model.entity.system.SysUser;
import com.atguigu.spzx.model.vo.common.ResultCodeEnum;
import com.atguigu.spzx.model.vo.system.LoginVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SysUserServiceImpl implements SysUserService {
    // 注入SysUserMapper用于操作用户数据
    @Autowired
    private SysUserMapper sysUserMapper;

    // 注入RedisTemplate用于操作Redis缓存
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 用户登录方法
     *
     * @param loginDto 包含用户登录信息的DTO，包括用户名和密码
     * @return 登录成功后返回包含token的LoginVo对象
     * @throws RuntimeException 如果用户名不存在或密码不正确，则抛出运行时异常
     */
    @Override
    public LoginVo login(LoginDto loginDto) {
       //获取输入验证码和存储到redis的key名称 loginDto获取到
        String captcha=loginDto.getCaptcha();
        String codeKey=loginDto.getCodeKey();

        //2 根据获取的redis里面key,查询redis里面存储验证码
        String redisCode=redisTemplate.opsForValue().get("user:login:validatecode:" + codeKey);

        //3 比较输入的验证码和redis里面存储的验证码是否一致
        //4 如果不一致，提示用户，校验失败
        if(StrUtil.isEmpty(redisCode) || !StrUtil.equalsIgnoreCase(redisCode , captcha)) {
            throw new GuiguException(ResultCodeEnum.VALIDATECODE_ERROR) ;
        }


        //5 如果一致，删除redis里面验证码
        redisTemplate.delete("user:login:validatecode:" + codeKey) ;
        // 1 获取提交用户名,loginDto获取到
        String userName = loginDto.getUserName();
        if (userName == null || userName.isEmpty()) {
            throw new GuiguException(ResultCodeEnum.USER_NAME_NOT_EXISTS);
        }
        // 2 根据用户名查询数据库sys_user表
        SysUser sysUser = sysUserMapper.selectUserInfoByUserName(userName);

        // 3 如果根据用户名查不到对应信息，用户不存在，返回错误信息
        if (sysUser == null) {
            throw new GuiguException(ResultCodeEnum.LOGIN_ERROR);
        }

        // 4 如果根据用户名查询的用户信息，用户存在
        // 5 获取输入的密码，比较输入的密码和数据库密码是否一致
        String database_password = sysUser.getPassword();
        String input_password =
                DigestUtils.md5DigestAsHex(loginDto.getPassword().getBytes());

        // 比较
        if (!database_password.equals(input_password)){
            throw new GuiguException(ResultCodeEnum.LOGIN_ERROR);
        }

        // 6 如果密码一致，登录成功，如果不一致则登录失败
        // 7 登录成功，生成用户唯一标识token
        String token=UUID.randomUUID().toString().replaceAll("-", "");

        // 8 把登录成功用户信息放到redis里面
        redisTemplate.opsForValue()
                .set("user:login" + token,
                        JSON.toJSONString(sysUser),
                        7,
                        TimeUnit.DAYS);

        // 9 返回Loginvo对象
        LoginVo loginVo = new LoginVo();
        loginVo.setToken(token);
        return loginVo;
    }
}
