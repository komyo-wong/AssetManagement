package com.assetmanagement.mqtt.application;

import com.assetmanagement.device.domain.Gateway;
import com.assetmanagement.mqtt.domain.MqttInboxMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MqttInboxQueryServiceTest {

    @Test
    void extractsGatewayMacFromHcbgAndNativePayloads() {
        assertThat(MqttInboxQueryService.extractGatewayMac("{\"pkt_type\":\"scan_report\",\"gw_addr\":\"140A02F36B62\"}"))
                .isEqualTo("140a02f36b62");
        assertThat(MqttInboxQueryService.extractGatewayMac("网关：30eda0caa70c,报文类型:gw_status,状态:online"))
                .isEqualTo("30eda0caa70c");
        assertThat(MqttInboxQueryService.extractGatewayMac("{\"type\":\"gw_status\",\"gw\":\"30:ed:a0:ca:a7:0c\",\"st\":\"online\"}"))
                .isEqualTo("30eda0caa70c");
        assertThat(MqttInboxQueryService.extractGatewayMac("")).isEmpty();
        assertThat(MqttInboxQueryService.extractGatewayMac(
                "网关：20e7c8d2be14,报文类型:adv_srp,目标设备:{'addr': 'f696ee5b7d63', 'rssi': -70}"))
                .isEqualTo("20e7c8d2be14");
    }

    @Test
    void prefersParsedReporterMacOverDeviceAddrInPayload() {
        String payload = "网关：20e7c8d2be14,报文类型:adv_srp,目标设备:{'addr': 'f696ee5b7d63'}";
        MqttInboxMessage message = new MqttInboxMessage(
                UUID.randomUUID(), UUID.randomUUID(), null, "GwData", 0, false, payload, "a".repeat(64), "k");
        message.markParsed("adv_srp", Map.of("gatewayMac", "20e7c8d2be14"));
        assertThat(MqttInboxQueryService.resolveGatewayMac(message)).isEqualTo("20e7c8d2be14");
    }

    @Test
    void duplicateMacUsesSelectedGatewayLabel() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Gateway thirdParty = gateway(tenantId, projectId, "006", "第三方网关", "f6:96:ee:5b:7d:63", Gateway.VENDOR_NATIVE);
        Gateway hcbg = gateway(tenantId, projectId, "0009", "GW_140A02F36B62", "f696ee5b7d63", Gateway.VENDOR_HCBG);
        Map<String, String> names = MqttInboxQueryService.gatewayNamesFrom(List.of(thirdParty, hcbg), hcbg);
        assertThat(names.get("f696ee5b7d63")).isEqualTo("0009 GW_140A02F36B62");
    }

    @Test
    void duplicateMacPrefersHcbgWhenUnfiltered() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Gateway thirdParty = gateway(tenantId, projectId, "006", "第三方网关", "f696ee5b7d63", Gateway.VENDOR_NATIVE);
        Gateway hcbg = gateway(tenantId, projectId, "0009", "GW_140A02F36B62", "f696ee5b7d63", Gateway.VENDOR_HCBG);
        Map<String, String> names = MqttInboxQueryService.gatewayNamesFrom(List.of(thirdParty, hcbg), null);
        assertThat(names.get("f696ee5b7d63")).isEqualTo("0009 GW_140A02F36B62");
    }

    private static Gateway gateway(
            UUID tenantId,
            UUID projectId,
            String code,
            String name,
            String mac,
            String vendor
    ) {
        Gateway gateway = new Gateway(tenantId, projectId, code, name);
        gateway.setVendor(vendor);
        gateway.update(name, mac, null, null, null, null, null, "ONLINE");
        return gateway;
    }
}
