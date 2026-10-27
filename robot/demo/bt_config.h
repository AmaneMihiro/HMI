#ifndef BT_CONFIG_H__
#define BT_CONFIG_H__
#include "component/common/types.h"

/* 载荷是下面两个结构体，小端、无填充。增删成员就改结构体。
 * 外层帧：0xA5 | type | len | payload[len] | xor。xor 从 type 异或到 payload 末字节。
 * 手机按住蜂鸣器时每 BT_BUZZER_REPEAT_MS 重发整份 bt_cmd_t。
 * 超过 BT_BUZZER_HOLD_MS 没收到新命令，蜂鸣器停止，LED 保持上一份命令。
 * 板子 10 Hz 发送 bt_report_t。角度单位是度。 */

#define BT_SOF                 0xA5u
#define BT_TYPE_REPORT         0x01u
#define BT_TYPE_CMD            0x02u
#define BT_PAYLOAD_MAX         32u

#define BT_BUZZER_REPEAT_MS    200u
#define BT_BUZZER_HOLD_MS      1000u

/* 手机 -> 板 */
typedef struct
{
    uint8_t buzzer;     /* 1 按下，0 松开 */
    uint8_t led;        /* 1 亮，0 灭 */
} BYTE_PACKED bt_cmd_t;

/* 板 -> 手机 */
typedef struct
{
    uint8_t valid;      /* 1 姿态有效 */
    float   roll_deg;
    float   pitch_deg;
    float   yaw_deg;
    uint8_t led;        /* 当前灯 */
    uint8_t buzzer;     /* 当前蜂鸣器 */
} BYTE_PACKED bt_report_t;
#endif
