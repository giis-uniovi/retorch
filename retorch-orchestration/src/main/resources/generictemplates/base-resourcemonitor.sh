#!/bin/bash
# Copyright (c) Software Engineering Research Group (GIIS), University of Oviedo.
# All intellectual property rights on this file belong to GIIS.
# The resourcemonitor.sh script measures the CPU, memory and storage used by the containers of a TJob.
#   start <TJobName> : launches a background sampler (every RETORCH_MONITOR_INTERVAL seconds, default 5)
#   stop  <TJobName> : stops the sampler of the TJob
#   export           : writes $WORKSPACE/artifacts/usageprofile-<branch>-<build>.csv, the single usage file with the
#                      lifecycle phases of the TJobs (taken from the lifecycle file written by
#                      savetjoblifecycledata.sh, which is removed) and all the resource measurements
# Columns: kind;tjobname;stage;item;start;end;cpu_percent;mem_bytes;disk_bytes;image;image_bytes;volume_bytes
#   kind=phase     : item is the lifecycle phase of the TJob (the COI phases belong to the TJob named COI), from start to end
#   kind=sample    : item is the container, start is the instant of the sample (same clock as the phases), disk_bytes is
#                    the size of the writable layer of the container
#   kind=container : one row per container, the first time it is seen: image, size of the image and size of the volumes
#                    it mounts
# Memory and storage are expressed in bytes, CPU in percentage of one core.

DATA_DIR="$WORKSPACE/retorchcostestimationdata/exec$BUILD_NUMBER"
INTERVAL="${RETORCH_MONITOR_INTERVAL:-5}"
HEADER="kind;tjobname;stage;item;start;end;cpu_percent;mem_bytes;disk_bytes;image;image_bytes;volume_bytes"

# Converts "1.5GiB" / "12kB" / "0B" into bytes
to_bytes='function b(s,  n,u){n=s+0;u=s;gsub(/[0-9.]/,"",u);
  if(u~/^[Kk]i?B$/)n*=(u~/i/?1024:1000);else if(u~/^Mi?B$/)n*=(u~/i/?1048576:1000000);
  else if(u~/^Gi?B$/)n*=(u~/i/?1073741824:1000000000);else if(u~/^Ti?B$/)n*=(u~/i/?1099511627776:1000000000000);
  return int(n)}'

image_bytes() {
  docker image inspect -f '{{.Size}}' "$1" 2>/dev/null || echo 0
}

# Sum of the sizes of the volumes (named or anonymous) mounted by the container $1
volume_bytes() {
  local mounted
  mounted=$(docker inspect -f '{{range .Mounts}}{{if eq .Type "volume"}}{{.Name}} {{end}}{{end}}' "$1" 2>/dev/null)
  if [ -z "$mounted" ]; then echo 0; return; fi
  docker system df -v 2>/dev/null | awk -v want="$mounted" "$to_bytes"'
    BEGIN{n=split(want,w," ");for(i=1;i<=n;i++)m[w[i]]=1}
    /^Local Volumes space usage/{on=1;next}
    on&&/^VOLUME NAME/{next}
    on&&NF==0{if(seen)on=0;next}
    on&&NF>=2{seen=1;if($1 in m)t+=b($NF)}
    END{print int(t)}'
}

# One "container" row for every container of the TJob that has not been seen before
new_containers() {
  local tjob="$1" ts="$2" seen="$DATA_DIR/seen-$1.txt" name image
  docker ps --filter "label=com.docker.compose.project=$tjob" --format '{{.Names}};{{.Image}}' |
    while IFS=';' read -r name image; do
      grep -qxF "$name" "$seen" 2>/dev/null && continue
      echo "$name" >>"$seen"
      printf 'container;%s;;%s;%s;;;;;%s;%s;%s\n' "$tjob" "$name" "$ts" "$image" "$(image_bytes "$image")" "$(volume_bytes "$name")"
    done
}

sample() {
  local tjob="$1" ts ids disk
  ts=$(date +%s%3N)
  ids=$(docker ps -q --filter "label=com.docker.compose.project=$tjob")
  [ -z "$ids" ] && return
  new_containers "$tjob" "$ts"
  disk=$(docker ps -s --filter "label=com.docker.compose.project=$tjob" --format '{{.Names}};{{.Size}}')
  # shellcheck disable=SC2086
  docker stats --no-stream --format '{{.Name}};{{.CPUPerc}};{{.MemUsage}}' $ids |
    awk -F';' -v ts="$ts" -v tjob="$tjob" -v disk="$disk" "$to_bytes"'
      BEGIN{n=split(disk,rows,"\n");for(i=1;i<=n;i++){split(rows[i],p,";");split(p[2],q," ");d[p[1]]=b(q[1])}}
      {split($3,m," / ");sub(/%/,"",$2);printf "sample;%s;;%s;%s;;%s;%d;%d;;;\n",tjob,$1,ts,$2,b(m[1]),d[$1]}'
}

# Lifecycle file (one row per TJob, one column per phase boundary) -> one "phase" row per TJob phase
phases() {
  awk -F';' 'NR>1{gsub(/\r/,"");
    printf "phase;%s;%s;tjob-setup;%s;%s;;;;;;\n",$1,$2,$5,$6;
    printf "phase;%s;%s;tjob-testexec;%s;%s;;;;;;\n",$1,$2,$7,$8;
    printf "phase;%s;%s;tjob-teardown;%s;%s;;;;;;\n",$1,$2,$9,$10;
    if(!coi){coi=1;
      printf "phase;COI;;COI-setup;%s;%s;;;;;;\n",$3,$4;
      printf "phase;COI;;coi-teardown;%s;%s;;;;;;\n",$11,$12}}' "$1"
}

case "$1" in
  start)
    mkdir -p "$DATA_DIR"
    out="$DATA_DIR/resources-$2.csv"
    : >"$out"
    : >"$DATA_DIR/seen-$2.txt"
    ( while true; do sample "$2" >>"$out"; sleep "$INTERVAL"; done ) >/dev/null 2>&1 &
    echo $! >"$DATA_DIR/resources-$2.pid"
    ;;
  stop)
    pidfile="$DATA_DIR/resources-$2.pid"
    if [ -f "$pidfile" ]; then
      kill "$(cat "$pidfile")" 2>/dev/null
      rm -f "$pidfile"
    fi
    ;;
  export)
    out="$WORKSPACE/artifacts/usageprofile-${BRANCH_NAME///}-${BUILD_NUMBER}.csv"
    lifecycle="$WORKSPACE/artifacts/lifecycletimingsexec${BRANCH_NAME///}-${BUILD_NUMBER}.csv"
    mkdir -p "$WORKSPACE/artifacts"
    echo "$HEADER" >"$out"
    if [ -f "$lifecycle" ]; then
      phases "$lifecycle" >>"$out"
      rm -f "$lifecycle"
    fi
    for f in "$DATA_DIR"/resources-*.csv; do
      [ -e "$f" ] && cat "$f" >>"$out"
    done
    ;;
  *)
    "$SCRIPTS_FOLDER/printLog.sh" "ERROR" "resourcemonitor" "Usage: $0 start|stop <TJobName> | export"
    exit 1
    ;;
esac
exit 0
