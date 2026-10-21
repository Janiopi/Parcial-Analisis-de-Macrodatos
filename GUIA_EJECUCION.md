# Guía simple para ejecutar las cinco consultas

Esta guía usa **rutas escritas completas**. Puedes copiar cada comando por separado: ninguno necesita una variable definida en un comando anterior.

Hay dos lugares distintos para los resultados:

| Modo | Dónde se ejecuta | Dónde quedan los resultados | Cómo se leen |
|---|---|---|---|
| Local | En este equipo o en una VM | Disco de esa misma máquina | `cat`, `ls` |
| Distribuido | Desde el master, con YARN | HDFS del clúster | `hdfs dfs -cat`, `hdfs dfs -ls` |

La compilación se hace en el proyecto de `janiopi`. La ejecución local de abajo está escrita para la cuenta `hadoop`, como en tu terminal. Primero copia el CSV y los JAR a `/home/hadoop/cc531` para que esa cuenta pueda leerlos y escribir sus resultados.

## 1. Compilar los JAR en este equipo

```bash
cd /home/janiopi/IdeaProjects/Parcial
bash scripts/build.sh
ls -lh dist/cc531*.jar
```

`cd` entra a la carpeta del proyecto. `scripts/build.sh` llama a Maven para compilar Java y deja los JAR en `dist/`. `ls` muestra los archivos generados. Necesitas Java 11, Maven y Hadoop 3.3.0 instalados para compilar o ejecutar según corresponda.

| JAR | Consulta |
|---|---|
| `dist/cc531-duration.jar` | Promedio de días hasta el cierre |
| `dist/cc531-interruption.jar` | Porcentaje de interrupciones por vía |
| `dist/cc531-dbscan.jar` | Grupos geográficos |
| `dist/cc531-mlp-class.jar` | Clasificación |
| `dist/cc531-mlp-reg.jar` | Regresión |

También existe `dist/cc531.jar`, que reúne las consultas. Para usarlo debes escribir `pe.edu.cc531.Main` y el nombre de la consulta después del JAR. Los ejemplos siguientes usan los JAR específicos, que requieren menos argumentos.

## 2. Ejecutar en modo local como `hadoop`

Desde la terminal que muestra `hadoop@pop-os:/home/janiopi/IdeaProjects/Parcial`, prepara una copia en tu carpeta personal:

```bash
mkdir -p /home/hadoop/cc531/dist /home/hadoop/cc531/temporal /home/hadoop/cc531/staging /home/hadoop/cc531/resultados
cp dist/cc531*.jar /home/hadoop/cc531/dist/
cp '3. Reporte Emergencias 01.01 al 20.08.2026 COES-MTC.csv' /home/hadoop/cc531/emergencias.csv
cd /home/hadoop/cc531
```

`mkdir -p` prepara las carpetas de la cuenta `hadoop`. Los dos `cp` copian los JAR y el CSV; `cd` entra a esa copia. Si alguno de los `cp` indica que no puede leer desde el proyecto, haz esas dos copias desde la cuenta `janiopi` y después vuelve a entrar como `hadoop`.

### Primera consulta: duración

```bash
hadoop jar dist/cc531-duration.jar \
  -Dmapreduce.jobtracker.staging.root.dir=/home/hadoop/cc531/staging \
  -Dhadoop.tmp.dir=/home/hadoop/cc531/temporal \
  -Dfs.defaultFS=file:/// \
  -Dmapreduce.framework.name=local \
  emergencias.csv \
  resultados/duration-01
```

Qué significa cada parte:

| Parte | Significado |
|---|---|
| `hadoop jar` | Ejecuta un programa Java MapReduce. |
| `dist/cc531-duration.jar` | Selecciona la consulta de duración. |
| `-Dmapreduce.jobtracker.staging.root.dir=...` | Mueve el área de preparación de jobs fuera de `/tmp/hadoop/mapred/staging`. Esta es la opción que corrige tu error. |
| `-Dhadoop.tmp.dir=...` | Indica una carpeta para otros archivos temporales. Por sí sola no cambia el área de preparación anterior. |
| `-Dfs.defaultFS=file:///` | Usa el disco local para entrada y salida. |
| `-Dmapreduce.framework.name=local` | Ejecuta los jobs en esta máquina, sin YARN. |
| `emergencias.csv` | Es el archivo de entrada que acabas de copiar. |
| `resultados/duration-01` | Es la carpeta de salida dentro de `/home/hadoop/cc531`. Debe **no existir** antes de ejecutar. |

Hadoop imprime muchos mensajes. Si el proceso termina con código `0` y los tres jobs dicen `completed successfully`, terminó bien. El resultado principal se lee así:

```bash
cat /home/hadoop/cc531/resultados/duration-01/result/part-r-00000
```

### Las otras cuatro consultas

Ejecuta desde `/home/hadoop/cc531`. Cada comando usa una carpeta de salida diferente:

```bash
hadoop jar dist/cc531-interruption.jar \
  -Dmapreduce.jobtracker.staging.root.dir=/home/hadoop/cc531/staging -Dhadoop.tmp.dir=/home/hadoop/cc531/temporal \
  -Dfs.defaultFS=file:/// -Dmapreduce.framework.name=local \
  emergencias.csv resultados/interruption-01

hadoop jar dist/cc531-dbscan.jar \
  -Dmapreduce.jobtracker.staging.root.dir=/home/hadoop/cc531/staging -Dhadoop.tmp.dir=/home/hadoop/cc531/temporal \
  -Dfs.defaultFS=file:/// -Dmapreduce.framework.name=local \
  emergencias.csv resultados/dbscan-01

hadoop jar dist/cc531-mlp-class.jar \
  -Dmapreduce.jobtracker.staging.root.dir=/home/hadoop/cc531/staging -Dhadoop.tmp.dir=/home/hadoop/cc531/temporal \
  -Dfs.defaultFS=file:/// -Dmapreduce.framework.name=local \
  emergencias.csv resultados/mlp-class-01 40

hadoop jar dist/cc531-mlp-reg.jar \
  -Dmapreduce.jobtracker.staging.root.dir=/home/hadoop/cc531/staging -Dhadoop.tmp.dir=/home/hadoop/cc531/temporal \
  -Dfs.defaultFS=file:/// -Dmapreduce.framework.name=local \
  emergencias.csv resultados/mlp-reg-01 40
```

El `40` final indica las épocas de entrenamiento del MLP; puede ser un número entre 1 y 500. Cada época es un job, así que estas consultas tardan más. Para repetir cualquier consulta cambia `-01` por `-02` en **la carpeta de salida**; Hadoop no reutiliza una salida existente.

Resultados locales:

```bash
cat /home/hadoop/cc531/resultados/interruption-01/result/part-r-00000
cat /home/hadoop/cc531/resultados/dbscan-01/davies-bouldin/part-*
head /home/hadoop/cc531/resultados/dbscan-01/assignments/part-*
cat /home/hadoop/cc531/resultados/mlp-class-01/metrics/part-*
cat /home/hadoop/cc531/resultados/mlp-reg-01/metrics/part-*
```

Si ejecutaste el modo local en una VM, esas carpetas están en **el disco de esa VM**. Para copiarlas de vuelta a este equipo, usa `scp -r`, igual que en el paso siguiente pero invirtiendo origen y destino.

## 3. Copiar JAR y CSV a las VM

Si vas a usar **el mismo equipo** para la prueba local anterior, no necesitas copiar nada. Para una VM separada de un nodo con usuario `hadoop`, sustituye `IP_VM` por la dirección real. Ejecuta desde este equipo con la cuenta `janiopi`:

```bash
cd /home/janiopi/IdeaProjects/Parcial
ssh hadoop@IP_VM 'mkdir -p ~/cc531/dist ~/cc531/temporal ~/cc531/staging ~/cc531/resultados'
scp dist/cc531*.jar hadoop@IP_VM:~/cc531/dist/
scp '3. Reporte Emergencias 01.01 al 20.08.2026 COES-MTC.csv' hadoop@IP_VM:~/cc531/emergencias.csv
```

`ssh` crea las carpetas de destino en la VM. `scp` copia los JAR y luego el CSV con un nombre corto. Para la prueba local en esa VM, entra con `ssh hadoop@IP_VM`, ejecuta `cd ~/cc531` y usa los comandos de ejecución de la sección 2.

En tu master, los JAR están en `~/laboratorio-mtc/jars` y el CSV ya está en HDFS como `/user/master/mtc/input/mtc.csv`. Si necesitas actualizar los JAR desde este equipo:

```bash
cd /home/janiopi/IdeaProjects/Parcial
ssh master@192.168.56.10 'mkdir -p ~/laboratorio-mtc/jars'
scp dist/cc531*.jar master@192.168.56.10:~/laboratorio-mtc/jars/
```

Solo envía el JAR al master. Al lanzar un job distribuido, Hadoop lo reparte a los workers. Si necesitas subir de nuevo el CSV, cópialo primero al master con `scp '3. Reporte Emergencias 01.01 al 20.08.2026 COES-MTC.csv' master@192.168.56.10:~/laboratorio-mtc/mtc.csv`; la sección siguiente explica cómo subirlo desde allí a HDFS.

## 4. Ejecutar en modo distribuido: HDFS y YARN

Ahora los comandos se ejecutan **dentro del master**:

```bash
ssh master@192.168.56.10
cd ~
ls -lh laboratorio-mtc/jars/cc531*.jar
hdfs getconf -confKey fs.defaultFS
hdfs getconf -confKey mapreduce.framework.name
yarn node -list
hdfs dfsadmin -report
```

`ls -lh` comprueba los JAR en el disco local del master. `fs.defaultFS` debe apuntar al NameNode del clúster; `mapreduce.framework.name` debe mostrar `yarn`. `yarn node -list` muestra los workers disponibles y `hdfs dfsadmin -report` muestra los DataNodes. Para probar distribución real, comprueba que aparezcan los dos workers previstos antes de lanzar los jobs.

Comprueba la entrada que ya tienes en HDFS y prepara la carpeta padre de resultados:

```bash
hdfs dfs -ls /user/master/mtc/input/mtc.csv
hdfs dfs -mkdir -p /user/master/cc531/resultados
hdfs dfs -ls /user/master/cc531
```

Si la primera comprobación indica que el CSV no existe, súbelo desde el disco del master:

```bash
hdfs dfs -mkdir -p /user/master/mtc/input
hdfs dfs -put -f laboratorio-mtc/mtc.csv /user/master/mtc/input/mtc.csv
hdfs dfs -ls /user/master/mtc/input/mtc.csv
```

`hdfs dfs -mkdir -p` crea carpetas **en HDFS**. `-put -f` sube el CSV y reemplaza una copia anterior si la hubiera.

Ejecuta las cinco consultas en el master. Aquí se usan los JAR locales del master y las rutas de entrada y salida **de HDFS**. No agregues las opciones `file:///` ni `local`:

```bash
hadoop jar laboratorio-mtc/jars/cc531-duration.jar /user/master/mtc/input/mtc.csv /user/master/cc531/resultados/duration-01
hadoop jar laboratorio-mtc/jars/cc531-interruption.jar /user/master/mtc/input/mtc.csv /user/master/cc531/resultados/interruption-01
hadoop jar laboratorio-mtc/jars/cc531-dbscan.jar /user/master/mtc/input/mtc.csv /user/master/cc531/resultados/dbscan-01
hadoop jar laboratorio-mtc/jars/cc531-mlp-class.jar /user/master/mtc/input/mtc.csv /user/master/cc531/resultados/mlp-class-01 40
hadoop jar laboratorio-mtc/jars/cc531-mlp-reg.jar /user/master/mtc/input/mtc.csv /user/master/cc531/resultados/mlp-reg-01 40
```

Los dos últimos comandos usan 40 épocas. Cada ruta de salida debe ser nueva; para repetir, usa `-02` en lugar de `-01`. Después de ejecutar `duration`, comprueba de inmediato que se creó su salida con `hdfs dfs -ls /user/master/cc531/resultados/duration-01/result`. Si esa ruta no aparece, el job no dejó el resultado esperado en HDFS: revisa la salida del comando y `fs.defaultFS` antes de seguir con las otras consultas.

### Ver los resultados en HDFS

También desde el master:

```bash
hdfs dfs -ls /user/master/cc531/resultados
hdfs dfs -ls /user/master/cc531/resultados/duration-01/result
hdfs dfs -cat /user/master/cc531/resultados/duration-01/result/part-*
hdfs dfs -cat /user/master/cc531/resultados/interruption-01/result/part-*
hdfs dfs -cat /user/master/cc531/resultados/dbscan-01/davies-bouldin/part-*
hdfs dfs -cat /user/master/cc531/resultados/mlp-class-01/metrics/part-*
hdfs dfs -cat /user/master/cc531/resultados/mlp-reg-01/metrics/part-*
```

`hdfs dfs -ls` lista archivos de HDFS. Sin indicar ruta, lista el directorio personal `/user/master`. En la salida que compartiste no aparece `cc531`, así que esa carpeta todavía no figuraba allí; el comando `hadoop jar` por sí solo no demuestra que se hayan escrito resultados. Después de crear `/user/master/cc531/resultados`, `cc531` debería aparecer en esa lista. `hdfs dfs -cat` muestra el contenido y `*` incluye todos los archivos `part-*` de una salida.

### Descargar de HDFS al disco y luego a este equipo

Primero, desde el master, descarga las carpetas de HDFS al **disco local del master**:

```bash
mkdir -p ~/laboratorio-mtc/descargas
hdfs dfs -get /user/master/cc531/resultados ~/laboratorio-mtc/descargas/
ls ~/laboratorio-mtc/descargas/resultados
```

Después, desde **este equipo**, copia esa carpeta del master:

```bash
cd /home/janiopi/IdeaProjects/Parcial
scp -r master@192.168.56.10:~/laboratorio-mtc/descargas/resultados ./resultados-del-cluster
```

`hdfs dfs -get` copia de HDFS al disco del master. `scp -r` copia del disco del master a este equipo. Si repites la descarga, elige una carpeta de destino distinta para no chocar con archivos existentes.

## Resumen de archivos de salida

| Consulta | Archivo importante dentro de su carpeta de salida |
|---|---|
| `duration` | `result/part-r-00000` |
| `interruption` | `result/part-r-00000` |
| `dbscan` | `assignments/part-*` y `davies-bouldin/part-*` |
| `mlp-class` | `metrics/part-*` y `predictions/part-*` |
| `mlp-reg` | `metrics/part-*` y `predictions/part-*` |
