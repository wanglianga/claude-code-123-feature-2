#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""既有功能快速回归（分期改造后）"""
import json, sys, urllib.request, urllib.error

BASE = "http://localhost:8080/api"
PASS, FAILS = 0, []

def req(method, path, token=None, body=None):
    r = urllib.request.Request(BASE + path,
                               data=json.dumps(body).encode() if body is not None else None, method=method)
    r.add_header("Content-Type", "application/json")
    if token: r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())

def check(name, cond, extra=""):
    global PASS
    if cond: PASS += 1; print("  ✓", name)
    else: FAILS.append(name + " " + str(extra)); print("  ✗", name, extra)

def login(u, p):
    s, pl = req("POST", "/auth/login", body={"username": u, "password": p})
    assert pl["code"] == 0, pl
    return pl["data"]["token"]

admin = login("admin", "admin123")
window = login("window", "window123")
zhang = login("zhang", "mediator123")
party = login("party", "party123")
assistant = login("assistant", "assistant123")
execution = login("execution", "exec123")
print("== 六类角色登录 ==")
check("六角色令牌均获取成功", all([admin, window, zhang, party, assistant, execution]))

print("== 案件列表与权限 ==")
s, pl = req("GET", "/cases", admin); check("管理员可见12+案件", len(pl["data"]) >= 12, len(pl["data"]))
s, pl = req("GET", "/cases?status=OVERDUE", execution)
check("按逾期状态筛选", all(c["status"] == "OVERDUE" for c in pl["data"]) and len(pl["data"]) >= 1)
s, pl = req("GET", "/cases", party)
check("当事人仅见本人案件(申请人账号)", all(c.get("applicantUserId") for c in pl["data"]))

print("== 电子送达 ==")
s, pl = req("POST", "/cases/1/deliveries", zhang,
            body={"target": "RESPONDENT", "documentName": "测试通知", "channel": "SMS"})
check("有号码送达成功", pl["data"]["status"] == "SENT", pl.get("message"))

print("== 常规履行回访仍可用 ==")
s, pl = req("POST", "/cases/6/follow-ups", execution,
            body={"result": "PARTIAL", "way": "PHONE", "note": "回归测试回访"})
check("新增常规回访成功", pl["code"] == 0 and pl["data"]["id"] is not None, pl.get("message"))

print("== 逾期扫描接口 ==")
s, pl = req("POST", "/overdue/scan", execution)
check("逾期扫描返回新增数", pl["code"] == 0 and "newlyOverdue" in pl["data"])

print("== 司法确认审查中案件 ==")
s, pl = req("GET", "/cases?status=JUDICIAL_CONFIRMING", assistant)
jc_cases = pl["data"]
check("存在司法确认审查中种子案件", len(jc_cases) >= 1, len(jc_cases))
jc_id = jc_cases[0]["id"]
dr = urllib.request.Request(BASE + f"/cases/{jc_id}/judicial-confirm-doc")
dr.add_header("Authorization", "Bearer " + assistant)
with urllib.request.urlopen(dr) as resp:
    doc = resp.read().decode()
check("司法确认申请书可下载", resp.status == 200 and "司法确认申请书" in doc)
s, pl = req("POST", f"/cases/{jc_id}/judicial-confirm/pass", assistant)
check("司法确认审查通过转履行", pl["data"]["status"] == "PERFORMING", pl.get("message"))

print("== 逾期案件材料卷含新跟踪章节 ==")
def dossier_text(case_id):
    rr = urllib.request.Request(BASE + f"/cases/{case_id}/dossier")
    rr.add_header("Authorization", "Bearer " + assistant)
    with urllib.request.urlopen(rr) as resp:
        return resp.read().decode()
d7 = dossier_text(7)
check("c7材料卷含按期提醒/逾期回访与观察章节",
      all(k in d7 for k in ["【按期履行提醒】", "【逾期回访与履行观察】", "未付款说明", "收款异议"]))
d6 = dossier_text(6)
check("c6材料卷含付款/收款凭证章节", "【付款/收款凭证】" in d6)

print("== 转立案材料包接收（c9） ==")
s, pl = req("POST", "/cases/9/transfer/accept", window, body={"litigationCaseNo": "(2026)回归第001号"})
check("窗口接收转立案并回写案号", pl["data"]["status"] == "TRANSFERRED" and "回归" in pl["data"]["litigationCaseNo"], pl.get("message"))

print("== CSV 批量导入 ==")
csv = "民间借贷,回归张三,13800000021,回归李四,13900000022,本市,否,1000,回归导入,借条,是,普通\n坏行少列\n物业费,回归物业,0571-86000099,回归王五,13700000023,小区,否,800,欠费,合同,是,普通"
s, pl = req("POST", "/import/cases", window, body={"csv": csv})
check("CSV导入2成功1失败", pl["data"]["success"] == 2 and pl["data"]["failed"] == 1, pl["data"])

print("== 看板 ==")
s, pl = req("GET", "/dashboard", admin); d = pl["data"]
check("KPI/履行/风险字段完整", all(k in d["kpi"] for k in ["total", "overdue", "observing", "pendingVoucherCount", "missing"]))
check("履行汇总含节点与金额", "fulfillRate" in d["performance"] and "paidSum" in d["performance"])

print("== 待办 ==")
s, pl = req("GET", "/todos", assistant)
check("法官助理待办正常返回", isinstance(pl["data"]["todos"], list))

print("== 前端静态资源 ==")
with urllib.request.urlopen("http://localhost:8080/") as r:
    idx = r.read().decode()
check("jar 提供 index.html", "<div id=\"app\"" in idx)

print(f"\n回归结果：通过 {PASS} 项，失败 {len(FAILS)} 项")
if FAILS:
    for f in FAILS: print("  FAIL:", f)
    sys.exit(1)
print("REGRESSION_PASSED")
