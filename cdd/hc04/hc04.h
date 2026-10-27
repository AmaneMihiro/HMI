#ifndef CDD_HC04_H__
#define CDD_HC04_H__
#include "component/common/types.h"
#include "bt_config.h"

/* HC-04 透传：UART7，115200 8N1，PE8(TX) 接模块 RX，PE7(RX) 接模块 TX。
 * 模块串口也要是 115200。经典蓝牙 SPP，UUID 00001101-0000-1000-8000-00805F9B34FB。
 * 载荷结构体在 robot/demo/bt_config.h。 */
typedef struct
{
    bt_cmd_t cmd;
    uint32_t cmd_ms;    /* 最近一次收到 bt_cmd_t 的时刻，没收到过是 0 */
} hc04_input_t;

void hc04_get_input(hc04_input_t *out);
int32_t hc04_send(uint8_t type, const void *payload, uint8_t length);
#endif
