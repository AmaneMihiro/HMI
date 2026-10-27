#include "cdd/hc04/hc04.h"
#include <string.h>
#include "bsp/uart/bsp_uart.h"
#include "bsp/tick/bsp_tick.h"
#include "osal/event.h"
#include "component/common/initcall.h"
#include "component/common/err.h"

_Static_assert(sizeof(bt_cmd_t) <= BT_PAYLOAD_MAX, "bt_cmd_t too big");
_Static_assert(sizeof(bt_report_t) <= BT_PAYLOAD_MAX, "bt_report_t too big");

enum
{
    RX_SOF,
    RX_TYPE,
    RX_LEN,
    RX_PAYLOAD,
    RX_XOR
};

static hc04_input_t input;
static uint8_t rx_state;
static uint8_t rx_type;
static uint8_t rx_len;
static uint8_t rx_index;
static uint8_t rx_xor;
static uint8_t rx_payload[BT_PAYLOAD_MAX];

static uint8_t payload_xor(uint8_t type, uint8_t length, const uint8_t *payload)
{
    uint8_t value = (uint8_t)(type ^ length);
    uint8_t index;

    for (index = 0u; index < length; index++)
    {
        value ^= payload[index];
    }
    return value;
}

static void accept_cmd(void)
{
    uint32_t key;

    if (rx_type != BT_TYPE_CMD || rx_len != sizeof(bt_cmd_t))
    {
        return;
    }
    key = os_irq_save();
    memcpy(&input.cmd, rx_payload, sizeof(bt_cmd_t));
    input.cmd_ms = bsp_tick_ms();
    os_irq_restore(key);
}

static void feed_byte(uint8_t byte)
{
    switch (rx_state)
    {
    case RX_SOF:
        if (byte == BT_SOF)
        {
            rx_state = RX_TYPE;
        }
        break;
    case RX_TYPE:
        rx_type = byte;
        rx_xor = byte;
        rx_state = RX_LEN;
        break;
    case RX_LEN:
        rx_len = byte;
        rx_xor ^= byte;
        rx_index = 0u;
        rx_state = (byte == 0u) ? RX_XOR : ((byte <= BT_PAYLOAD_MAX) ? RX_PAYLOAD : RX_SOF);
        break;
    case RX_PAYLOAD:
        rx_payload[rx_index++] = byte;
        rx_xor ^= byte;
        if (rx_index >= rx_len)
        {
            rx_state = RX_XOR;
        }
        break;
    case RX_XOR:
        if (byte == rx_xor)
        {
            accept_cmd();
        }
        rx_state = RX_SOF;
        break;
    default:
        rx_state = RX_SOF;
        break;
    }
}

static void hc04_run(void *argument, const uint8_t *data, uint16_t len)
{
    uint16_t index;

    (void)argument;
    if (data == NULL || len == 0u)
    {
        return;
    }
    for (index = 0u; index < len; index++)
    {
        feed_byte(data[index]);
    }
}
EVENT_TASK_DEFINE(hc04_task, OS_PRIORITY_MEDIUM, OS_STACK_MEDIUM, EVENT_QUEUE, 64, OS_QUEUE_LEN_8,
                  OS_TIMEOUT_NONE, hc04_run, NULL);

void hc04_get_input(hc04_input_t *out)
{
    uint32_t key = os_irq_save();

    *out = input;
    os_irq_restore(key);
}

int32_t hc04_send(uint8_t type, const void *payload, uint8_t length)
{
    uint8_t frame[3u + BT_PAYLOAD_MAX + 1u];
    uint8_t index;

    if ((payload == NULL && length != 0u) || length > BT_PAYLOAD_MAX)
    {
        return ERR_INVAL;
    }
    frame[0] = BT_SOF;
    frame[1] = type;
    frame[2] = length;
    if (length != 0u)
    {
        memcpy(&frame[3], payload, length);
    }
    frame[3u + length] = payload_xor(type, length, &frame[3]);
    index = (uint8_t)(4u + length);
    if (bsp_uart_write(UART_BT, frame, index) != (int32_t)index)
    {
        return ERR_BUSY;
    }
    return ERR_OK;
}

static int32_t hc04_init(void)
{
    memset(&input, 0, sizeof input);
    rx_state = RX_SOF;
    bsp_uart_bind_event(UART_BT, &hc04_task);
    return ERR_OK;
}
INIT_CDD_STAGE1(hc04_init);
