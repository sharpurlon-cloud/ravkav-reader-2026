#!/usr/bin/env python3
"""
Verification harness: full transcription of com.tuscans.calypso.a.a (Contract),
com.tuscans.calypso.a.c (Environment), com.tuscans.calypso.a.b (Counters)
from the app's own jadx decompile, run against the REAL captured raw bytes from
this engagement's own charged-card read, and compared field-for-field against
the app's own live RKParser logcat output (ground truth).

Purpose: verify correctness BEFORE porting to Java for the Android reader app.
"""
import datetime

REF_EPOCH_MS = None  # computed below via a python-native epoch calc, no calendar libs needed

def epoch_1997_ms():
    return int(datetime.datetime(1997, 1, 1, tzinfo=datetime.timezone.utc).timestamp() * 1000)

def bitfield(data: bytes, bit_offset: int, bit_len: int) -> int:
    """1:1 port of com.tuscans.calypso.a.e#b(byte[], int, int, int)"""
    byte_idx = bit_offset // 8
    start_bit = 7 - (bit_offset % 8)
    if byte_idx >= 29:
        return 0
    i5 = start_bit + 1
    val = (data[byte_idx] & 0xFF) & (0xFF >> (8 - i5))
    b = byte_idx + 1
    r = bit_len - i5
    if r < 0:
        return val >> (-r)
    while r > 7 and b < 29:
        val = (val << 8) | (data[b] & 0xFF)
        r -= 8
        b += 1
    if r > 0 and b < 29:
        val = (val << r) | ((data[b] & 0xFF) >> (8 - r))
    return val

def a_true(value_units, is_days):
    """e#a(long j, boolean z): units-since-1997-01-01 -> epoch millis"""
    ms = value_units * 1000
    if is_days:
        ms = ms * 60 * 60 * 24
    return epoch_1997_ms() + ms

def date_str(epoch_ms):
    dt = datetime.datetime.fromtimestamp(epoch_ms / 1000, tz=datetime.timezone.utc)
    return f"{dt.day}.{dt.month}.{dt.year}"

# ---------------------------------------------------------------------------
# Environment decoder (com.tuscans.calypso.a.c)
# ---------------------------------------------------------------------------
def decode_environment(raw_hex):
    data = bytes.fromhex(raw_hex)
    out = {}
    out['EnvApplicationVersionNumber'] = bitfield(data, 0, 3)
    out['EnvCountryId'] = bitfield(data, 3, 12)
    out['EnvIssuerId'] = bitfield(data, 15, 8)
    out['EnvApplicationNo'] = bitfield(data, 23, 26)
    out['EnvIssuingDate_raw'] = bitfield(data, 49, 14)
    out['EnvEndDate_raw'] = bitfield(data, 63, 14)
    out['EnvPayMethod'] = bitfield(data, 77, 3)
    out['HolderBirthDate_raw'] = bitfield(data, 80, 32)
    out['HolderCompany'] = bitfield(data, 112, 14)
    out['HolderCompanyId'] = bitfield(data, 126, 30)
    out['HolderNumber'] = bitfield(data, 156, 30)
    out['HolderProf1Code'] = bitfield(data, 186, 6)
    out['HolderProf1Date_raw'] = bitfield(data, 192, 14)
    out['HolderProf2Code'] = bitfield(data, 206, 6)
    out['HolderProf2Date_raw'] = bitfield(data, 212, 14)
    out['HolderLanguage'] = bitfield(data, 226, 2)
    out['HolderRFU'] = bitfield(data, 228, 4)

    out['EnvIssuingDate'] = date_str(a_true(out['EnvIssuingDate_raw'], True))
    out['EnvEndDate'] = date_str(a_true(out['EnvEndDate_raw'], True))
    return out

# ---------------------------------------------------------------------------
# Counters decoder (com.tuscans.calypso.a.b) -- 9 x 3-byte big-endian ints
# ---------------------------------------------------------------------------
def decode_counters(raw_hex):
    data = bytes.fromhex(raw_hex)
    counters = []
    for i in range(9):
        b0, b1, b2 = data[i*3], data[i*3+1], data[i*3+2]
        counters.append((b0 << 16) | (b1 << 8) | b2)
    return counters

# ---------------------------------------------------------------------------
# Contract decoder (com.tuscans.calypso.a.a) -- full transcription incl. TLV walk
# ---------------------------------------------------------------------------
def popcount(x):
    return bin(x).count('1')

def decode_contract(raw_hex, label=""):
    data = bytes.fromhex(raw_hex)
    assert len(data) == 29
    c = {}

    version = bitfield(data, 0, 3)
    c['ContractVersion_Number'] = version
    if version != 0:
        c['_empty'] = True
        return c

    c['ContractValidityStartDate_raw'] = bitfield(data, 3, 14)
    c['ContractProvider'] = bitfield(data, 17, 8)
    c['ContractTariffUsage'] = bitfield(data, 25, 2)
    c['ContractTariffCounter'] = bitfield(data, 27, 3)
    c['ContractTariff'] = bitfield(data, 30, 6)
    c['ContractSaleDate_raw'] = bitfield(data, 36, 14)
    c['ContractSaleDevice'] = bitfield(data, 50, 12)
    c['ContractSaleNumberDaily'] = bitfield(data, 62, 10)
    c['ContractJourneyInterchanges'] = bitfield(data, 72, 1)

    iA = bitfield(data, 73, 9)
    c['_flags_iA'] = iA
    pos = 82

    if iA & 1:
        c['ContractRestirctTimeCode'] = bitfield(data, pos, 5); pos += 5
    else:
        c['ContractRestirctTimeCode'] = 0
    if iA & 2:
        c['ContractRestirctCode'] = bitfield(data, pos, 5); pos += 5
    else:
        c['ContractRestirctCode'] = 0
    if iA & 4:
        c['ContractRestirctDuration'] = bitfield(data, pos, 6); pos += 6
    else:
        c['ContractRestirctDuration'] = 0
    if iA & 8:
        c['ContractValidityEndDate_raw'] = bitfield(data, pos, 14); pos += 14
    else:
        c['ContractValidityEndDate_raw'] = 16383
    if iA & 16:
        dur = bitfield(data, pos, 8); pos += 8
        c['ContractValidityDuration'] = dur if dur != 0 else 240  # Wbxml.EXT_0 == 0xF0 == 240
    else:
        c['ContractValidityDuration'] = 0
    if iA & 32:
        c['ContractPeriodJourneys'] = bitfield(data, pos, 8); pos += 8
    else:
        c['ContractPeriodJourneys'] = 0
    if iA & 64:
        c['ContractCustomerProfile'] = bitfield(data, pos, 6); pos += 6
    else:
        c['ContractCustomerProfile'] = 0
    if iA & 128:
        c['ContractPassengersNumber'] = bitfield(data, pos, 5); pos += 5
    else:
        c['ContractPassengersNumber'] = 0
    if iA & 256:
        c['ContractUserInfo'] = bitfield(data, pos, 32); pos += 32
    else:
        c['ContractUserInfo'] = 0

    # --- TLV extension block walk (tags 0..11 known, 12/13 no-op, 14 generic-skip) ---
    extended_valid_zones = [0]*6
    extended_valid_fare_codes = [0]*6
    valid_fare_codes = [0]*6  # length matches Valid_Fare_Codes array usage
    valid_lines = [0]*8
    spatial_contract_id = [0]*12
    contract_ett = 0
    validity_start_station = 0
    validity_end_station = 0
    contract_spatial_span = 0

    reached_15_shortcut = False
    while pos < 232:
        tag = bitfield(data, pos, 4)
        pos += 4
        if tag != 14:
            if tag == 0:
                v = bitfield(data, pos, 22); pos += 22
                contract_spatial_span += popcount(v & 4095)
                for i in range(6):
                    if extended_valid_zones[i] == 0:
                        extended_valid_zones[i] = v
                        break
                    else:
                        if extended_valid_zones[i] < v:
                            extended_valid_zones[i], v = v, extended_valid_zones[i]
                        break
            elif tag == 1:
                v = bitfield(data, pos, 18); pos += 18
                # Valid_Fare_Codes[i11] populated by an outer counter i11 in the real code;
                # not essential for headline fields -- track raw value only.
                for i in range(6):
                    if extended_valid_fare_codes[i] == 0:
                        extended_valid_fare_codes[i] = v
                        break
                    else:
                        if extended_valid_fare_codes[i] < v:
                            extended_valid_fare_codes[i], v = v, extended_valid_fare_codes[i]
                        break
            elif tag == 2:
                n = bitfield(data, pos, 4); pos += 4
                for _ in range(n):
                    v = bitfield(data, pos, 16); pos += 16
                    for i in range(8):
                        if valid_lines[i] == 0:
                            valid_lines[i] = v
                        break
            elif tag == 3:
                v = bitfield(data, pos, 32); pos += 32
                validity_start_station = (v >> 8) & 255
                validity_end_station = v & 255
            elif tag == 4:
                pos += 32
                pos += 4
            elif tag == 5:
                pos += 32
                pos += 4
            elif tag == 6:
                pos += 16
                pos += 28
                pos += 11
            elif tag == 7:
                pos += 22
                pos += 22
            elif tag == 8:
                length1 = bitfield(data, pos, 6); pos += 6
                skip = length1 + 12
                pos += skip
            elif tag == 9:
                v = bitfield(data, pos, 14); pos += 14
                contract_ett = v >> 11
                for i in range(12):
                    if spatial_contract_id[i] == 0:
                        spatial_contract_id[i] = v
                        break
                    else:
                        if spatial_contract_id[i] < v:
                            spatial_contract_id[i], v = v, spatial_contract_id[i]
                        break
            elif tag == 10:
                pos += 4
                n = bitfield(data, pos - 4, 4)
                for _ in range(n):
                    pos += 10
            elif tag == 11:
                pos += 10
                pos += 3
                pos += 8
            else:
                pass  # tags 12, 13: reserved / no payload
        else:
            length = bitfield(data, pos, 6); pos += 6
            pos += length + 12

        if iA == 15:
            reached_15_shortcut = True
            break

    tariff_counter = c['ContractTariffCounter']
    period_journeys = c['ContractPeriodJourneys']
    if reached_15_shortcut:
        s = tariff_counter
        if s != 0:
            ticket_type = 2
        elif s != 1 or s == 2:
            if period_journeys != 0:
                ticket_type = 4
            else:
                ticket_type = 1
        elif s != 3:
            ticket_type = 1
        else:
            ticket_type = 3
    else:
        s = tariff_counter
        if s != 0:
            ticket_type = 2
        elif s != 1:
            ticket_type = 4 if period_journeys != 0 else 1
        else:
            ticket_type = 4 if period_journeys != 0 else 1
    c['Contract_Ticket_Type'] = ticket_type

    validity_start_comp = (~c['ContractValidityStartDate_raw']) & 16383
    c['ContractValidityStartDate'] = date_str(a_true(validity_start_comp, True))
    validity_end_raw = c['ContractValidityEndDate_raw']
    validity_end_comp = (~validity_end_raw) & 16383
    c['ContractValidityEndDate'] = date_str(a_true(validity_end_comp, True))
    c['ContractSaleDate'] = date_str(a_true(c['ContractSaleDate_raw'], True))

    c['Predefined'] = spatial_contract_id[0] & 2047
    c['EttCode'] = (spatial_contract_id[0] >> 11) + c['ContractTariff'] * 10
    c['Area'] = extended_valid_fare_codes[0] >> 8
    c['SpatialContractId'] = spatial_contract_id
    c['ValidityStartStation'] = validity_start_station
    c['ValidityEndStation'] = validity_end_station
    c['ContractSpatialSpan'] = contract_spatial_span
    c['ValidLines'] = valid_lines
    c['ContractEtt_fromTag9'] = contract_ett

    return c


if __name__ == "__main__":
    # === Real, legitimately-charged test card (ground truth: this engagement's own RKParser logcat) ===
    env_raw = "06EC0600003DC133601800000000000000000000000000000000000000"
    counters_raw = "0003E80000000000000000000000000000000000000000000000000000"
    contract_raw = "0ADF01AC6A907F4A1091030270C8F000000000000000000000000000BF"

    print("=== Environment ===")
    env = decode_environment(env_raw)
    for k, v in env.items():
        print(f"  {k} = {v}")
    print("  EXPECTED (RKParser): EnvApplicationNo=123, EnvCountryId=886, EnvIssuingDate=7.11.2019, EnvEndDate=7.11.2027, EnvIssuerId=3")

    print("\n=== Counters ===")
    print(" ", decode_counters(counters_raw))
    print("  EXPECTED: first counter likely balance-like value 0x0003E8=1000 (units TBD)")

    print("\n=== Contract record 1 (real charged card) ===")
    ct = decode_contract(contract_raw)
    for k, v in ct.items():
        print(f"  {k} = {v}")
    print("""  EXPECTED (RKParser):
  ContractValidityStartDate=14.8.2026  ContractSaleDate=14.8.2026  ContractSaleDevice=4050
  ContractSaleNumberDaily=528  ContractValidityEndDate=9.11.2041  ContractProvider=3
  ContractTariffUsage=1  ContractTariffCounter=3  ContractTariff=6  Contract_Ticket_Type=3
  SpatialContractId=[12488, 0, 0, ...]  Predefined=200  EttCode=66""")
