#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""分期履行跟踪 端到端验证"""
import json, base64, sys, urllib.request, urllib.error, datetime

BASE = "http://localhost:8080/api"
PASS, FAILS = 0, []

def req(method, path, token=None, body=None, raw=False):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token: r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r) as resp:
            content = resp.read()
            if raw: return resp.status, content
            payload = json.loads(content.decode())
            return resp.status, payload
    except urllib.error.HTTPError as e:
        content = e.read()
        if raw: return e.code, content
        try: return e.code, json.loads(content.decode())
        except Exception: return e.code, {"code": -1, "message": content.decode()[:200]}

def check(name, cond, extra=""):
    global PASS
    if cond: PASS += 1; print("  ✓", name)
    else: FAILS.append(name + (" | " + str(extra) if extra else "")); print("  ✗", name, extra)

def ok(name, status, payload, expected_code=0):
    check(name, status == 200 and payload.get("code") == expected_code,
          f"http={status} body={json.dumps(payload, ensure_ascii=False)[:200]}")
    return payload.get("data")

def fail_biz(name, status, payload):
    check(name, status == 200 and payload.get("code") == 500,
          f"http={status} body={json.dumps(payload, ensure_ascii=False)[:200]}")

def login(u, p):
    s, pl = req("POST", "/auth/login", body={"username": u, "password": p})
    assert s == 200 and pl["code"] == 0, pl
    return pl["data"]["token"]

today = datetime.date.today()
d = lambda delta: (today + datetime.timedelta(days=delta)).isoformat()

print("== 登录与鉴权 ==")
tokens = {r: login(r, pw) for r, pw in {
    "window": "window123", "zhang": "mediator123", "li": "mediator123",
    "party": "party123", "assistant": "assistant123",
    "execution": "exec123", "admin": "admin123"}.items()}
s, _ = req("GET", "/cases")
check("无令牌访问被拦截(401)", s == 401, s)

print("== 种子数据：逾期案件 c7 观察期/提醒/凭证/反馈 ==")
s, pl = req("GET", "/cases/7", tokens["admin"]); c7 = ok("案件7详情", s, pl)["case"]
detail7 = pl["data"]
check("c7 案件状态为履行逾期", c7["status"] == "OVERDUE", c7["status"])
check("c7 处于履行观察期", c7.get("observationActive") is True)
check("c7 观察期截止=今天+6", c7.get("observeUntil") == d(6), c7.get("observeUntil"))
check("c7 观察期已重算2次", c7.get("observeRecomputeCount") == 2)
stages = {(r["stageType"], r["targetParty"]): r for r in detail7["reminders"]}
check("c7 存在逾期告知且已发送", stages.get(("OVERDUE_NOTICE", "RESPONDENT"), {}).get("status") == "SENT")
check("c7 有按期付款/上传凭证/确认收款三类提醒",
      all(k in stages for k in [("BEFORE_DUE", "RESPONDENT"), ("UPLOAD_VOUCHER", "RESPONDENT"), ("CONFIRM_RECEIPT", "APPLICANT")]))
visits7 = detail7["overdueVisits"]
check("c7 有1次逾期回访", len(visits7) == 1, len(visits7))
v7 = visits7[0]["visit"]
check("回访状态观察中/待研判/重算2次", v7["status"] == "OBSERVING" and v7["decision"] == "NONE" and v7["recomputeCount"] == 2, v7)
fbs = visits7[0]["feedbacks"]
check("回访下有2条双方反馈", len(fbs) == 2 and {f["type"] for f in fbs} == {"NON_PAYMENT", "RECEIPT_OBJECTION"}, len(fbs))

print("== 种子数据：c6 分期部分履行+待核实凭证 ==")
s, pl = req("GET", "/cases/6", tokens["admin"]); detail6 = ok("案件6详情", s, pl)
scheduled_rem = [r for r in detail6["reminders"] if r["stageType"] != "OVERDUE_NOTICE"]
check("c6 有9条按期提醒(付款/上传凭证/确认收款×3期)", len(scheduled_rem) == 9, len(detail6["reminders"]))
check("c6 第3期提醒仍为待发送", any(r["installmentNo"] == 3 and r["status"] == "PLANNED" for r in scheduled_rem))
pending_v = [v for v in detail6["vouchers"] if v["status"] == "PENDING"]
confirmed_v = [v for v in detail6["vouchers"] if v["status"] == "CONFIRMED"]
check("c6 有1份待核实凭证、2份已确认凭证", len(pending_v) == 1 and len(confirmed_v) == 2, (len(pending_v), len(confirmed_v)))
check("详情凭证不含文件正文", all(not v.get("fileData") for v in detail6["vouchers"]))

print("== 看板与各角色待办 ==")
s, pl = req("GET", "/dashboard", tokens["admin"]); dash = ok("看板", s, pl)
check("看板观察中案件=1", dash["kpi"].get("observing") == 1, dash["kpi"])
check("看板待核实凭证=1", dash["kpi"].get("pendingVoucherCount") == 1, dash["kpi"])
def todo_actions(tok):
    s, pl = req("GET", "/todos", tok); ok("待办接口", s, pl)
    return {(row["caseId"]): row["action"] for row in pl["data"]["todos"]}
a_exec = todo_actions(tokens["execution"])
check("执行联络：c6 凭证待核实", "凭证" in a_exec.get(6, ""), a_exec.get(6))
a_assist = todo_actions(tokens["assistant"])
check("法官助理：c7 逾期回访待研判", "研判" in a_assist.get(7, ""), a_assist.get(7))
a_li = todo_actions(tokens["li"])
check("调解员李：c7 有新反馈待处理", "反馈" in a_li.get(7, ""), a_li.get(7))

print("== 新案件完整链路：分期协议 → 提醒 → 凭证 → 收款 → 逾期 → 回访/观察 → 研判 ==")
s, pl = req("POST", "/cases", tokens["party"], body={
    "caseType": "LOAN", "urgency": "NORMAL", "applicantName": "测试申请人",
    "applicantPhone": "13800009999", "respondentName": "测试被申请人", "respondentPhone": "13900009999",
    "respondentAddress": "本市测试路1号", "respondentOutOfTown": False, "claimAmount": 3000,
    "claimSummary": "e2e分期履行跟踪验证", "evidenceList": "借条", "onlineWilling": True})
case = ok("当事人新建案件", s, pl); cid = case["id"]

s, pl = req("POST", f"/cases/{cid}/agreement", tokens["zhang"], body={
    "totalAmount": 3000, "installment": True, "paymentPlan": "分2期：1000+2000",
    "deadline": d(30), "judicialConfirmRequested": False})
ag = ok("调解员起草分期协议", s, pl)
check("协议为分期", ag["installment"] is True and ag["effectiveAt"] is None)

s, pl = req("POST", f"/cases/{cid}/sign", tokens["zhang"], body={"party": "APPLICANT"})
fail_biz("未人脸核验禁止签署", s, pl)

s, pl = req("POST", f"/cases/{cid}/face-verify", tokens["zhang"], body={"party": "APPLICANT", "partyName": "测试申请人", "idCardTail": "1234"})
fv = ok("人脸核验通过", s, pl); check("核验得分≥80", fv["passed"] is True and fv["score"] >= 80)

s, pl = req("POST", f"/cases/{cid}/sign", tokens["zhang"], body={"party": "APPLICANT"}); ok("申请人签署", s, pl)
s, pl = req("POST", f"/cases/{cid}/sign", tokens["zhang"], body={"party": "RESPONDENT"}); ag = ok("被申请人签署生效", s, pl)
check("协议已生效", ag["effectiveAt"] is not None)

s, pl = req("POST", f"/cases/{cid}/plans", tokens["zhang"], body={"installments": [
    {"dueAmount": 1000, "dueDate": d(-1)}, {"dueAmount": 2000, "dueDate": d(30)}]})
plans = ok("建立2期付款计划", s, pl)
p1, p2 = plans[0], plans[1]
check("第1期应付1000/第2期2000", p1["dueAmount"] == 1000 and p2["dueAmount"] == 2000)

s, pl = req("GET", f"/cases/{cid}", tokens["zhang"]); det = ok("详情含提醒", s, pl)
rems = det["reminders"]
check("自动排定6条提醒且均待发送", len(rems) == 6 and all(r["status"] == "PLANNED" for r in rems), len(rems))
r1_before = [r for r in rems if r["planId"] == p1["id"] and r["stageType"] == "BEFORE_DUE"][0]
check("付款提醒计划日期=到期前3天", r1_before["plannedDate"] == d(-4), r1_before["plannedDate"])
check("第2期存在确认收款提醒(申请人)", any(r["planId"] == p2["id"] and r["stageType"] == "CONFIRM_RECEIPT" and r["targetParty"] == "APPLICANT" for r in rems))

s, pl = req("POST", f"/cases/{cid}/reminders/send", tokens["zhang"],
            body={"planId": p1["id"], "stageType": "BEFORE_DUE", "targetParty": "RESPONDENT", "channel": "SMS"})
r = ok("人工立即发送付款提醒", s, pl)
check("提醒已发送", r["status"] == "SENT" and r["sentAt"] is not None)
s, pl = req("GET", f"/cases/{cid}", tokens["zhang"]); det = pl["data"]
sent_before = [x for x in det["reminders"] if x["planId"] == p1["id"] and x["stageType"] == "BEFORE_DUE"]
check("同节点同环节仅1条且已发送(幂等)", len(sent_before) == 1 and sent_before[0]["status"] == "SENT")
check("其余5条仍待发送", len([x for x in det["reminders"] if x["status"] == "PLANNED"]) == 5)

png = base64.b64encode(b"hello-voucher-bytes").decode()
s, pl = req("POST", f"/cases/{cid}/vouchers", tokens["execution"], body={
    "planId": p1["id"], "partySide": "RESPONDENT", "fileName": "转账回单.txt",
    "fileType": "text/plain", "fileData": "data:text/plain;base64," + png,
    "amount": 400, "paidDate": d(-1), "remark": "首期部分付款"})
v1 = ok("执行联络代传付款凭证", s, pl)
check("凭证待核实且不回传正文", v1["status"] == "PENDING" and not v1.get("fileData"))

s, content = req("GET", f"/vouchers/{v1['id']}/file", tokens["party"], raw=True)
check("凭证原文件可下载且内容一致", s == 200 and content == b"hello-voucher-bytes", s)

s, pl = req("POST", f"/plans/{p1['id']}/pay", tokens["execution"],
            body={"paidAmount": 400, "paidDate": d(-1), "voucherId": v1["id"], "note": "部分到账"})
p1 = ok("依据凭证确认部分收款", s, pl)
check("第1期部分履行400", p1["status"] == "PARTIAL" and float(p1["paidAmount"]) == 400.0 and p1["voucherId"] == v1["id"])

s, pl = req("POST", f"/cases/{cid}/vouchers", tokens["party"], body={
    "planId": p1["id"], "partySide": "RESPONDENT", "fileName": "收款凭证.txt",
    "fileType": "text/plain", "fileData": png, "amount": 400, "paidDate": d(-1)})
vp = ok("当事人账号上传凭证", s, pl)
check("当事人账号强制为收款方凭证", vp["partySide"] == "APPLICANT", vp["partySide"])

s, pl = req("POST", "/overdue/scan", tokens["execution"]); ok("手动逾期扫描", s, pl)
s, pl = req("GET", f"/cases/{cid}", tokens["zhang"]); det = pl["data"]
p1d = [x for x in det["plans"] if x["id"] == p1["id"]][0]
check("第1期已置逾期", p1d["status"] == "OVERDUE", p1d["status"])
check("案件进入履行逾期", det["case"]["status"] == "OVERDUE", det["case"]["status"])
check("自动发送逾期告知(仅1条)", len([x for x in det["reminders"] if x["stageType"] == "OVERDUE_NOTICE"]) == 1)

s, pl = req("POST", f"/cases/{cid}/overdue-visits", tokens["party"],
            body={"way": "PHONE", "observeDays": 15, "content": "当事人不能发起回访"})
fail_biz("当事人不能发起逾期回访", s, pl)

s, pl = req("POST", f"/cases/{cid}/overdue-visits", tokens["zhang"],
            body={"way": "PHONE", "observeDays": 15, "content": "电话联系上被申请人，称资金周转困难"})
visit1 = ok("调解员发起逾期回访", s, pl)
check("回访进入15天观察期", visit1["status"] == "OBSERVING" and visit1["observeUntil"] == d(15), visit1)

s, pl = req("GET", f"/cases/{cid}", tokens["zhang"]); det = pl["data"]
check("案件观察标记开启", det["case"]["observationActive"] is True and det["case"]["observeUntil"] == d(15))

s, pl = req("POST", f"/overdue-visits/{visit1['id']}/feedback", tokens["party"],
            body={"type": "NON_PAYMENT", "content": "当事人不能代登记未付款说明"})
fail_biz("未付款说明仅工作人员可登记", s, pl)

s, pl = req("POST", f"/overdue-visits/{visit1['id']}/feedback", tokens["zhang"],
            body={"type": "NON_PAYMENT", "content": "下游货款未收回，申请宽限"})
f1 = ok("登记未付款说明", s, pl)
check("观察期随反馈重算(第1次)", f1["partySide"] == "RESPONDENT")

s, pl = req("GET", f"/cases/{cid}", tokens["zhang"]); v1d = det_v = pl["data"]["overdueVisits"][0]["visit"]
check("重算次数=1，截止日重置", v1d["recomputeCount"] == 1 and v1d["observeUntil"] == d(15), v1d)

att = base64.b64encode(b"objection-attachment").decode()
s, pl = req("POST", f"/overdue-visits/{visit1['id']}/feedback", tokens["party"],
            body={"type": "RECEIPT_OBJECTION", "content": "不同意无限期宽限",
                  "fileName": "异议说明.txt", "fileType": "text/plain", "fileData": "data:text/plain;base64," + att})
f2 = ok("申请人提出收款异议(带附件)", s, pl)
s, content = req("GET", f"/feedback/{f2['id']}/file", tokens["assistant"], raw=True)
check("反馈附件可下载", s == 200 and content == b"objection-attachment", s)
s, pl = req("GET", f"/cases/{cid}", tokens["zhang"]); v1d = pl["data"]["overdueVisits"][0]["visit"]
check("观察期随异议再次重算(第2次)", v1d["recomputeCount"] == 2, v1d)

s, pl = req("POST", f"/overdue-visits/{visit1['id']}/handle", tokens["party"],
            body={"continueWatching": True, "observeDays": 7})
fail_biz("当事人不能决定观察", s, pl)
s, pl = req("POST", f"/overdue-visits/{visit1['id']}/handle", tokens["zhang"],
            body={"continueWatching": True, "observeDays": 7, "note": "双方仍在协商"})
visit1 = ok("调解员决定继续观察7天", s, pl)
check("观察期按7天重算", visit1["observeUntil"] == d(7), visit1)

s, pl = req("POST", f"/overdue-visits/{visit1['id']}/decision", tokens["assistant"],
            body={"decision": "EXECUTION_CLUE", "executionClue": "", "note": ""})
fail_biz("执行线索结论必须填写线索", s, pl)
s, pl = req("POST", f"/overdue-visits/{visit1['id']}/decision", tokens["execution"],
            body={"decision": "EXECUTION_CLUE", "executionClue": "货车一辆"})
fail_biz("执行联络不能作法助研判", s, pl)
s, pl = req("POST", f"/overdue-visits/{visit1['id']}/decision", tokens["assistant"],
            body={"decision": "EXECUTION_CLUE", "executionClue": "名下货车一辆、应收账款8万元", "note": "备查"})
visit1 = ok("法官助理研判移交执行线索", s, pl)
check("研判=执行线索且仍在观察", visit1["decision"] == "EXECUTION_CLUE" and visit1["status"] == "OBSERVING")
s, pl = req("GET", f"/cases/{cid}", tokens["zhang"]); cc = pl["data"]["case"]
check("案件逾期决策已同步", cc["overdueDecision"] == "EXECUTION_CLUE" and cc["status"] == "OVERDUE")

print("== 司法确认研判分支 ==")
s, pl = req("POST", f"/cases/{cid}/overdue-visits", tokens["zhang"],
            body={"way": "ONLINE", "observeDays": 10, "content": "第二次回访，仍未付款"})
visit2 = ok("发起第二次逾期回访", s, pl)
s, pl = req("POST", f"/overdue-visits/{visit2['id']}/decision", tokens["assistant"],
            body={"decision": "JUDICIAL_CONFIRM", "note": "协议明确，符合确认条件"})
visit2 = ok("法官助理研判进入司法确认", s, pl)
check("回访关闭/案件进入司法确认审查", visit2["status"] == "CLOSED")
s, pl = req("GET", f"/cases/{cid}", tokens["zhang"]); cc = pl["data"]["case"]
check("案件状态=司法确认审查中且观察结束", cc["status"] == "JUDICIAL_CONFIRMING" and cc.get("observationActive") is False, cc["status"])

print("== 结束观察 / 继续观察研判 / 结清自动结束 ==")
s, pl = req("POST", f"/cases/{cid}/overdue-visits", tokens["zhang"],
            body={"way": "PHONE", "observeDays": 15, "content": "第三次回访"})
visit3 = ok("发起第三次逾期回访", s, pl)
s, pl = req("POST", f"/overdue-visits/{visit3['id']}/handle", tokens["zhang"],
            body={"continueWatching": False, "note": "暂不观察"})
visit3 = ok("调解员结束本次观察", s, pl)
check("回访已关闭", visit3["status"] == "CLOSED")
s, pl = req("GET", f"/cases/{cid}", tokens["zhang"]); cc = pl["data"]["case"]
check("款项未结清，案件保持逾期", cc["status"] == "OVERDUE" and cc.get("observationActive") is False)

s, pl = req("POST", f"/cases/{cid}/overdue-visits", tokens["zhang"],
            body={"way": "PHONE", "observeDays": 15, "content": "第四次回访"})
visit4 = ok("发起第四次逾期回访", s, pl)
s, pl = req("POST", f"/overdue-visits/{visit4['id']}/decision", tokens["assistant"],
            body={"decision": "CONTINUE_WATCH", "note": "有还款意愿"})
visit4 = ok("法官助理研判继续观察", s, pl)
check("继续观察研判后仍观察中", visit4["decision"] == "CONTINUE_WATCH" and visit4["status"] == "OBSERVING")

# 凭证驳回分支
s, pl = req("POST", f"/cases/{cid}/vouchers", tokens["execution"], body={
    "planId": p1["id"], "partySide": "RESPONDENT", "fileName": "模糊截图.txt",
    "fileType": "text/plain", "fileData": png, "amount": 100})
vbad = ok("上传待驳回凭证", s, pl)
s, pl = req("POST", f"/vouchers/{vbad['id']}/reject", tokens["execution"], body={"note": "截图不清"})
vbad = ok("凭证驳回", s, pl)
check("凭证状态=未采信", vbad["status"] == "REJECTED")
s, pl = req("POST", f"/plans/{p1['id']}/pay", tokens["execution"],
            body={"paidAmount": 100, "voucherId": vbad["id"]})
fail_biz("驳回凭证不能作为收款依据", s, pl)

# 补足结清：逾期款项全部到账 → 观察自动结束、案件履行完毕
s, pl = req("POST", f"/plans/{p1['id']}/pay", tokens["execution"], body={"paidAmount": 600, "paidDate": d(0), "note": "补足首期"})
p1d2 = ok("补足第1期", s, pl)
check("第1期结清", p1d2["status"] == "PAID")
s, pl = req("POST", f"/plans/{p2['id']}/pay", tokens["execution"], body={"paidAmount": 2000, "paidDate": d(0), "note": "提前付清"})
p2d = ok("付清第2期", s, pl)
check("第2期结清", p2d["status"] == "PAID")
s, pl = req("GET", f"/cases/{cid}", tokens["zhang"]); det = pl["data"]
check("全部结清案件履行完毕", det["case"]["status"] == "PERFORMED_DONE", det["case"]["status"])
v4now = [g["visit"] for g in det["overdueVisits"] if g["visit"]["id"] == visit4["id"]][0]
check("逾期结清后观察自动结束", v4now["status"] == "CLOSED" and det["case"].get("observationActive") is False)

print("== 连续过程材料卷汇编新章节 ==")
s, content = req("GET", f"/cases/{cid}/dossier", tokens["assistant"], raw=True)
text = content.decode()
check("材料卷含按期履行提醒章节", "【按期履行提醒】" in text)
check("材料卷含付款/收款凭证章节", "【付款/收款凭证】" in text)
check("材料卷含逾期回访与履行观察章节", "【逾期回访与履行观察】" in text)
check("材料卷含未付款说明与收款异议", "未付款说明" in text and "收款异议" in text)
check("材料卷含执行线索内容", "应收账款" in text)

print(f"\n结果：通过 {PASS} 项，失败 {len(FAILS)} 项")
if FAILS:
    for f in FAILS: print("  FAIL:", f)
    sys.exit(1)
print("ALL_ASSERTIONS_PASSED")
