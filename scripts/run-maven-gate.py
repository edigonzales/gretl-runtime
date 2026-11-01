#!/usr/bin/env python3
"""Forward one Maven process, preserve its exit, and bound termination of its own process group."""
import os
from pathlib import Path
import signal
import subprocess
import sys

output=Path(sys.argv[1]);argv=sys.argv[2:]
process=None
interrupted=0

def terminate():
    if process is None or process.poll() is not None:return
    os.killpg(process.pid,signal.SIGTERM)
    try:process.wait(timeout=30)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid,signal.SIGKILL);process.wait(timeout=5)

def stop(signum,frame):
    global interrupted
    interrupted=128+signum
    terminate()

signal.signal(signal.SIGTERM,stop);signal.signal(signal.SIGINT,stop)
code=1
try:
    with (output/'maven.log').open('wb') as log:
        process=subprocess.Popen(argv,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,start_new_session=True)
        (output/'maven-pid.txt').write_text(str(process.pid)+'\n')
        while True:
            block=process.stdout.read1(65536)
            if not block:break
            log.write(block);log.flush();sys.stdout.buffer.write(block);sys.stdout.buffer.flush()
        code=process.wait()
        if code<0:code=128-code
except (BrokenPipeError,KeyboardInterrupt):
    terminate();code=interrupted or 1
finally:
    terminate()
    code=interrupted or code
    (output/'maven-exit.txt').write_text(str(code)+'\n')
sys.exit(code)
