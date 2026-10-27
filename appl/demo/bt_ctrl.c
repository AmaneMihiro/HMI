/* 手机经 HC-04 看姿态，并控制蜂鸣器和 LED。载荷结构体在 robot/demo/bt_config.h。
 * 板载灯和蜂鸣器的自动演示关掉，避免和这里抢。 */
#include "osal/period.h"
#include "bt_config.h"
#include "cdd/hc04/hc04.h"
#include "cdd/imu/ins/ins.h"
#include "cdd/buzzer/buzzer.h"
#include "cdd/led/ws2812/ws2812.h"
#include "bsp/tick/bsp_tick.h"

#define RAD_TO_DEG         57.2957795f
#define BUZZER_TONE_HZ     2000u
#define LED_BRIGHT         32u

static void apply_led(bool on)
{
    if (on)
    {
        ws2812_set(0, LED_BRIGHT, LED_BRIGHT, LED_BRIGHT);
    }
    else
    {
        ws2812_set(0, 0, 0, 0);
    }
    (void)ws2812_refresh();
}

static void bt_ctrl_run(void *argument)
{
    ins_attitude_t attitude;
    hc04_input_t command;
    bt_report_t report = {0};
    static bool led_on;
    static bool sounding;
    bool led_request;
    bool buzzer_request;
    bool fresh;

    (void)argument;
    hc04_get_input(&command);
    fresh = command.cmd_ms != 0u && (bsp_tick_ms() - command.cmd_ms) < BT_BUZZER_HOLD_MS;
    buzzer_request = fresh && command.cmd.buzzer != 0u;
    led_request = command.cmd.led != 0u;
    if (buzzer_request)
    {
        buzzer_on(BUZZER_TONE_HZ);
        sounding = true;
    }
    else if (sounding)
    {
        buzzer_off();
        sounding = false;
    }
    led_on = led_request;
    apply_led(led_on);

    ins_get_attitude(&attitude);
    report.valid = attitude.valid ? 1u : 0u;
    report.roll_deg = attitude.roll_rad * RAD_TO_DEG;
    report.pitch_deg = attitude.pitch_rad * RAD_TO_DEG;
    report.yaw_deg = attitude.yaw_rad * RAD_TO_DEG;
    report.led = led_on ? 1u : 0u;
    report.buzzer = sounding ? 1u : 0u;
    (void)hc04_send(BT_TYPE_REPORT, &report, (uint8_t)sizeof report);
}
PERIOD_TASK_DEFINE(bt_ctrl_task, OS_FREQ_100HZ, OS_PRIORITY_LOW, OS_STACK_MEDIUM, bt_ctrl_run, NULL);
