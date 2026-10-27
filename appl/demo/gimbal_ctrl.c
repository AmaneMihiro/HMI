/* 云台：CAN2，pitch = gm6020，yaw = j4310。角度都用本板 IMU。
 * yaw 速度环仍用电机自己的转速。
 * 左拨杆上或中时用手控。下拨杆或 IMU 无效时输出关掉。 */
#include "osal/period.h"
#include "component/common/initcall.h"
#include "component/common/err.h"
#include "cdd/remote/dt7/dt7.h"
#include "cdd/imu/ins/ins.h"
#include "cdd/motor/motor_init.h"

#define GIMBAL_DT_S              0.001f
#define GIMBAL_PITCH_LIMIT_RAD   0.45f
#define DT7_SWITCH_UP            1u
#define DT7_SWITCH_MID           3u
#define STICK_MAX                660.0f
#define STICK_IGNORE             20
#define YAW_RATE_RAD_S           2.0f
#define PITCH_RATE_RAD_S         1.0f

static ins_attitude_t gimbal_attitude;
static bool pitch_on;
static bool yaw_on;
static float pitch_target;
static float yaw_target;
static float yaw_measure;

static float stick_to_rate(int16_t raw, float max_rad_s)
{
    if (raw > -STICK_IGNORE && raw < STICK_IGNORE)
    {
        return 0.0f;
    }
    return (float)raw / STICK_MAX * max_rad_s;
}

static float clamp_pitch(float angle)
{
    if (angle > GIMBAL_PITCH_LIMIT_RAD)
    {
        return GIMBAL_PITCH_LIMIT_RAD;
    }
    if (angle < -GIMBAL_PITCH_LIMIT_RAD)
    {
        return -GIMBAL_PITCH_LIMIT_RAD;
    }
    return angle;
}

static int32_t gimbal_ctrl_init(void)
{
    motor_bind_angle_feedback(&pitch_gm6020.base, &gimbal_attitude.pitch_rad);
    motor_bind_speed_feedback(&pitch_gm6020.base, &gimbal_attitude.gyro_rad_s[1]);
    motor_bind_angle_feedback(&yaw_j4310.base, &yaw_measure);
    return dm_motor_enable(&yaw_j4310);
}
INIT_APP_STAGE2(gimbal_ctrl_init);

static void gimbal_ctrl_run(void *argument)
{
    dt7_data_t remote;
    bool enabled = false;
    float yaw_rate_rad_s = 0.0f;
    float pitch_rate_rad_s = 0.0f;
    bool usable;

    (void)argument;
    ins_get_attitude(&gimbal_attitude);
    dt7_get(&remote);
    if (remote.online &&
        (remote.switch_left == DT7_SWITCH_UP || remote.switch_left == DT7_SWITCH_MID))
    {
        enabled = true;
        yaw_rate_rad_s = stick_to_rate(remote.channel[0], -YAW_RATE_RAD_S);
        pitch_rate_rad_s = stick_to_rate(remote.channel[1], PITCH_RATE_RAD_S);
    }
    usable = enabled && gimbal_attitude.valid;

    if (usable)
    {
        if (!pitch_on)
        {
            pitch_target = gimbal_attitude.pitch_rad;
            yaw_target = gimbal_attitude.yaw_rad;
        }
        else
        {
            pitch_target = clamp_pitch(pitch_target + pitch_rate_rad_s * GIMBAL_DT_S);
            yaw_target += yaw_rate_rad_s * GIMBAL_DT_S;
        }
        yaw_measure = gimbal_attitude.yaw_rad;
        if (!pitch_on)
        {
            motor_set_active(&pitch_gm6020.base, true);
            pitch_on = true;
        }
        if (!yaw_on)
        {
            motor_set_active(&yaw_j4310.base, true);
            yaw_on = true;
        }
        (void)motor_set_target(&pitch_gm6020.base, pitch_target);
        (void)motor_set_target(&yaw_j4310.base, yaw_target);
    }
    else
    {
        if (pitch_on)
        {
            motor_set_active(&pitch_gm6020.base, false);
            pitch_on = false;
        }
        if (yaw_on)
        {
            motor_set_active(&yaw_j4310.base, false);
            yaw_on = false;
        }
    }
    (void)motor_compute_bus(CAN_BUS2);
    (void)motor_send_bus(CAN_BUS2);
}
PERIOD_TASK_DEFINE(gimbal_ctrl_task, OS_FREQ_1000HZ, OS_PRIORITY_MEDIUM, OS_STACK_MEDIUM, gimbal_ctrl_run, NULL);
