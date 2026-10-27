ifeq ($(CONFIG_ROBOT),y)
    # -I robot/<r>，cdd 里的注册代码包含当前兵种的 *_config.h。
    MODULE_INC += $(d)$(r)
    ifeq ($(r),demo)
        # 表在本目录的头文件里。
    else
        $(error unknown robot r=$(r); add a branch in robot/module.mk)
    endif
endif
