#!/usr/bin/env python3

import argparse
import getpass
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.parse
import urllib.request


ROOT = Path(__file__).resolve().parent.parent
PRIVATE_DIR = ROOT / ".publishing"
KEY_DIR = PRIVATE_DIR / "gnupg"
CREDENTIALS_FILE = PRIVATE_DIR / "credentials.json"
PENDING_TOKEN_FILE = PRIVATE_DIR / "central-token.json"
KEYSERVER = "https://keyserver.ubuntu.com"


def run(command, **options):
    return subprocess.run(command, cwd=ROOT, check=True, **options)


def prepare_private_directory():
    result = subprocess.run(
        ["git", "check-ignore", "--quiet", ".publishing/credentials.json"],
        cwd=ROOT,
    )
    if result.returncode != 0:
        raise RuntimeError("请先在项目 .gitignore 中忽略 /.publishing/")
    for directory in (PRIVATE_DIR, KEY_DIR):
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        directory.chmod(0o700)


def gpg_command(*arguments):
    executable = shutil.which("gpg")
    if not executable:
        raise RuntimeError("未安装 GnuPG，请先执行 brew install gnupg")
    return [executable, "--homedir", str(KEY_DIR), *arguments]


def primary_fingerprint():
    result = run(
        gpg_command("--batch", "--with-colons", "--list-secret-keys"),
        capture_output=True,
        text=True,
    )
    for line in result.stdout.splitlines():
        fields = line.split(":")
        if fields[0] == "fpr":
            return fields[9]
    return None


def read_passphrase(confirm=False):
    phrase = getpass.getpass("GPG 密钥口令（输入不回显）：")
    if not phrase or "\n" in phrase:
        raise RuntimeError("密钥口令不能为空或包含换行")
    if confirm and phrase != getpass.getpass("再次输入 GPG 密钥口令："):
        raise RuntimeError("两次密钥口令不一致")
    return phrase


def setup():
    if CREDENTIALS_FILE.exists():
        raise RuntimeError("已有本地配置；可执行 check、upload 或 publish-key，无需重复 setup")
    gpg_command("--version")
    if PENDING_TOKEN_FILE.is_file():
        token = json.loads(PENDING_TOKEN_FILE.read_text())
        username = token.get("username", "").strip()
        password = token.get("password", "").strip()
        print("已读取本项目保存的 Central Token，无需重复输入。")
    else:
        print("在 https://central.sonatype.com/usertoken 生成 User Token。")
        print("请填写生成结果中的 Username / Password，不是网站登录密码。")
        username = getpass.getpass("Central Token Username（输入不回显）：").strip()
        password = getpass.getpass("Central Token Password（输入不回显）：").strip()
    if not username or not password:
        raise RuntimeError("Central Token 的两项值都不能为空")
    fingerprint = primary_fingerprint()
    if not fingerprint:
        print("签名公钥会发布到公钥服务器，以下名字和邮箱将随公钥公开。")
        name = input("签名者名字/昵称 [x2ink]：").strip() or "x2ink"
        email = input("签名者邮箱：").strip()
        if "@" not in email or any(character in name + email for character in "\r\n<>"):
            raise RuntimeError("请填写有效名字和邮箱")
        phrase = read_passphrase(confirm=True)
        run(
            gpg_command(
                "--batch", "--pinentry-mode", "loopback", "--passphrase-fd", "0",
                "--quick-generate-key", f"{name} <{email}>", "rsa3072", "sign", "2y",
            ),
            input=phrase + "\n",
            text=True,
            timeout=120,
        )
        fingerprint = primary_fingerprint()
    if not fingerprint:
        raise RuntimeError("未能生成签名密钥")
    payload = {"username": username, "password": password, "fingerprint": fingerprint}
    descriptor = os.open(CREDENTIALS_FILE, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as output:
        json.dump(payload, output)
    PENDING_TOKEN_FILE.unlink(missing_ok=True)
    print("Token 和密钥已保存在本项目被忽略的 .publishing/ 目录；密钥口令未写入配置文件。")
    print("请妥善备份该目录和你设置的密钥口令。")
    publish_key()


def load_credentials():
    if not CREDENTIALS_FILE.is_file():
        raise RuntimeError("尚未配置发布凭据，请先执行 python3 scripts/publish.py setup")
    payload = json.loads(CREDENTIALS_FILE.read_text())
    if not all(payload.get(field) for field in ("username", "password", "fingerprint")):
        raise RuntimeError("本地发布配置不完整")
    return payload


def publish_key():
    credentials = load_credentials()
    public_key = run(
        gpg_command("--armor", "--export", credentials["fingerprint"]),
        capture_output=True,
    ).stdout
    if not public_key:
        raise RuntimeError("未找到对应公钥")
    (PRIVATE_DIR / "public-key.asc").write_bytes(public_key)
    print("正在发布签名公钥：" + credentials["fingerprint"], flush=True)
    request = urllib.request.Request(
        KEYSERVER + "/pks/add",
        data=urllib.parse.urlencode({"keytext": public_key.decode("ascii")}).encode("ascii"),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    # 使用与服务器网页相同的 HTTPS 接口，避免依赖 dirmngr 的独立网络配置。
    with urllib.request.urlopen(request, timeout=25) as response:
        response.read()
    print("公钥已提交，正在从服务器读取并核对指纹。", flush=True)
    query = urllib.parse.urlencode({"op": "get", "search": "0x" + credentials["fingerprint"]})
    with urllib.request.urlopen(KEYSERVER + "/pks/lookup?" + query, timeout=20) as response:
        downloaded = response.read()
    inspected = run(
        gpg_command("--batch", "--with-colons", "--show-keys"),
        input=downloaded,
        capture_output=True,
    ).stdout.decode("utf-8")
    fingerprints = [line.split(":")[9] for line in inspected.splitlines() if line.startswith("fpr:")]
    if credentials["fingerprint"] not in fingerprints:
        raise RuntimeError("服务器返回的公钥指纹不匹配")
    print("公钥已发布且指纹核验通过；Central 端同步可能需要时间。")


def publishing_environment(credentials):
    phrase = read_passphrase()
    exported = run(
        gpg_command(
            "--batch", "--pinentry-mode", "loopback", "--passphrase-fd", "0",
            "--armor", "--export-secret-keys", credentials["fingerprint"],
        ),
        input=phrase + "\n",
        capture_output=True,
        text=True,
    ).stdout
    if not exported:
        raise RuntimeError("未能读取签名私钥")
    environment = dict(os.environ)
    environment.update({
        "ORG_GRADLE_PROJECT_mavenCentralUsername": credentials["username"],
        "ORG_GRADLE_PROJECT_mavenCentralPassword": credentials["password"],
        "ORG_GRADLE_PROJECT_signingInMemoryKey": exported,
        "ORG_GRADLE_PROJECT_signingInMemoryKeyPassword": phrase,
    })
    studio_jdk = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home")
    if not environment.get("JAVA_HOME") and studio_jdk.is_dir():
        environment["JAVA_HOME"] = str(studio_jdk)
    return environment


def verify_signatures():
    signatures = list((ROOT / "logkit/build").rglob("*.asc"))
    if len(signatures) < 4:
        raise RuntimeError("签名附件不足，未生成完整发布产物")
    for signature in signatures:
        original = signature.with_suffix("")
        if not original.is_file():
            raise RuntimeError("找不到签名对应文件：" + str(original))
        run(gpg_command("--batch", "--verify", str(signature), str(original)))
    print(f"已核验 {len(signatures)} 个本地文件签名。")


def build_or_upload(upload):
    credentials = load_credentials()
    environment = publishing_environment(credentials)
    # 签名私钥通过环境变量传递，关闭配置缓存避免将其序列化到 Gradle 缓存。
    gradle = [str(ROOT / "gradlew"), "--no-configuration-cache", "--console=plain"]
    run(gradle + [":logkit:signMavenPublication"], env=environment)
    verify_signatures()
    if upload:
        run(gradle + [":logkit:publishToMavenCentral"], env=environment)
        print("上传任务完成。请到 Central 的 Deployments 检查校验结果，再点击 Publish。")
    else:
        print("本地构建与签名检查完成；没有上传到 Central。")


def main():
    parser = argparse.ArgumentParser(description="LogKit 本地配置、签名与 Maven Central 上传")
    parser.add_argument("command", choices=("setup", "publish-key", "check", "upload"))
    arguments = parser.parse_args()
    if arguments.command != "publish-key" and not sys.stdin.isatty():
        raise RuntimeError("请在你自己的交互式终端中运行，以便安全输入凭据和口令")
    prepare_private_directory()
    if arguments.command == "setup":
        setup()
    elif arguments.command == "publish-key":
        publish_key()
    else:
        build_or_upload(arguments.command == "upload")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as failure:
        print("操作未完成：" + str(failure), file=sys.stderr)
        raise SystemExit(1)
    except (KeyboardInterrupt, EOFError):
        print("\n已取消操作。", file=sys.stderr)
        raise SystemExit(1)
