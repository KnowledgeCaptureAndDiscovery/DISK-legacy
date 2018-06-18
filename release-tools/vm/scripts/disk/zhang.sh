#!/bin/bash

set -e


# -------------
# Zhang et. al.
# -------------


yum -y install gcc ncurses-devel ImageMagick
pip install --upgrade Pillow

# ---------
# SAM tools
# ---------

SAMTOOLS_VERSION='1.3.1'

curl --location \
     --output samtools-${SAMTOOLS_VERSION}.tar.bz2 \
     "https://github.com/samtools/samtools/releases/download/${SAMTOOLS_VERSION}/samtools-${SAMTOOLS_VERSION}.tar.bz2"
tar --bzip2 --extract --verbose --file samtools-${SAMTOOLS_VERSION}.tar.bz2

cd samtools-${SAMTOOLS_VERSION}

./configure
make
make install

cd ..
rm --recursive --force samtools-${SAMTOOLS_VERSION}*


#----------
# BCF Tools
# ---------

BCFTOOLS_VERSION='1.3.1'

curl --location \
     --output bcftools-${BCFTOOLS_VERSION}.tar.bz2 \
     "https://github.com/samtools/bcftools/releases/download/${BCFTOOLS_VERSION}/bcftools-${BCFTOOLS_VERSION}.tar.bz2"
tar --bzip2 --extract --verbose --file bcftools-${BCFTOOLS_VERSION}.tar.bz2

cd bcftools-${BCFTOOLS_VERSION}

make
make install

cd ..
rm --recursive --force bcftools-${BCFTOOLS_VERSION}*


# -------
# Bowtie2
# -------

BOWTIE2_VERSION='2.2.9'

curl --location \
     --output bowtie2-${BOWTIE2_VERSION}-linux-x86_64.zip \
     "http://downloads.sourceforge.net/project/bowtie-bio/bowtie2/${BOWTIE2_VERSION}/bowtie2-${BOWTIE2_VERSION}-linux-x86_64.zip?r=https://sourceforge.net/projects/bowtie-bio/files/bowtie2/${BOWTIE2_VERSION}/&ts=`date +\"%s\"`&use_mirror=heanet"
unzip bowtie2-${BOWTIE2_VERSION}-linux-x86_64.zip

cd bowtie2-${BOWTIE2_VERSION}
cp bowtie2* /usr/local/bin/

cd ..
rm --recursive --force bowtie2-${BOWTIE2_VERSION}*


# -------
# Tophat2
# -------

TOPHAT2_VERSION='2.1.1'

curl --location \
     --output tophat-${TOPHAT2_VERSION}.Linux_x86_64.tar.gz \
     "https://ccb.jhu.edu/software/tophat/downloads/tophat-${TOPHAT2_VERSION}.Linux_x86_64.tar.gz"
tar --gzip --extract --verbose --file tophat-${TOPHAT2_VERSION}.Linux_x86_64.tar.gz

cd tophat-${TOPHAT2_VERSION}.Linux_x86_64

rm --force AUTHORS LICENSE README
cp --recursive --force * /usr/local/bin

cd ..
rm --recursive --force tophat-${TOPHAT2_VERSION}.Linux_x86_64*


# ---------
# Cufflinks
# ---------

CUFFLINKS_VERSION='2.2.1'

curl --location \
     --output cufflinks-${CUFFLINKS_VERSION}.Linux_x86_64.tar.gz \
     "http://cole-trapnell-lab.github.io/cufflinks/assets/downloads/cufflinks-${CUFFLINKS_VERSION}.Linux_x86_64.tar.gz"
tar --gzip --extract --verbose --file cufflinks-${CUFFLINKS_VERSION}.Linux_x86_64.tar.gz

cd cufflinks-${CUFFLINKS_VERSION}.Linux_x86_64

rm --force AUTHORS LICENSE README
cp --force * /usr/local/bin

cd ..
rm --recursive --force cufflinks-${CUFFLINKS_VERSION}.Linux_x86_64*


# ---------
# BED Tools
# ---------

BEDTOOLS_VERSION='2.27.1'

curl --location \
     --output bedtools2.tar.gz \
     "https://github.com/arq5x/bedtools2/releases/download/v${BEDTOOLS_VERSION}/bedtools-${BEDTOOLS_VERSION}.tar.gz"
tar --gzip --extract --verbose --file bedtools2.tar.gz

cd bedtools2
make
make install

cd ..
rm --recursive --force bedtools2*


# -------------
# Cell Profiler
# -------------

JAVA_VERSION=`java -version 2>&1 | head -1 | sed -e 's/.*"\(.*\)_.*"/\1/g'`

yum -y install numpy scipy python-zmq java-${JAVA_VERSION}-openjdk-devel Cython MySQL-python gcc-c++
pip install matplotlib
git clone https://github.com/CellProfiler/CellProfiler.git /usr/local/cellprofiler
pushd /usr/local/cellprofiler > /dev/null
git checkout v3.0.0
pip install -e . --process-dependency-links
popd > /dev/null
yum -y remove Cython


# -----
# RSeQC
# -----

yum -y install lzo-devel
pip install RSeQC


# ----------
# R Packages
# ----------

yum -y install R-devel openssl-devel libcurl-devel

Rscript - << EOT
install.packages("codetools", repos='http://cran.us.r-project.org')
install.packages("devtools", repos='http://cran.us.r-project.org')
install.packages("curl", repos='http://cran.us.r-project.org')
library(devtools)
install_github("kassambara/easyGgplot2")
install.packages("ggplot2", repos='http://cran.us.r-project.org')
install.packages("reshape", repos='http://cran.us.r-project.org')
install.packages("reshape2", repos='http://cran.us.r-project.org')
install.packages("dplyr", repos="http://R-Forge.R-project.org")
install.packages("Vennerable", repos="http://R-Forge.R-project.org")
install.packages("PerformanceAnalytics", repos="http://R-Forge.R-project.org")
install.packages("psych", repos='http://cran.us.r-project.org')
install.packages("circlize", repos='http://cran.us.r-project.org')
install.packages("corrplot", repos='http://cran.us.r-project.org')
install.packages("Hmisc", repos='http://cran.us.r-project.org')
install.packages("gdata", repos='http://cran.us.r-project.org')
install.packages("factoextra", dependencies=TRUE, repos='http://cran.rstudio.com/', type='source')
install.packages("scagnostics", repos='http://cran.us.r-project.org')
source('http://bioconductor.org/biocLite.R')
biocLite()
biocLite("customProDB")
biocLite("Rsubread")
biocLite("limma")
biocLite("ComplexHeatmap")
biocLite("ConsensusClusterPlus")
EOT


# --------
# MSGFPlus
# --------

curl --location \
     --output MSGFPlus.zip \
     --header "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36" \
     "https://omics.pnl.gov/sites/default/files/MSGFPlus.zip"
unzip -o -d /usr/local/msgfplus MSGFPlus.zip
rm --force MSGFPlus.zip


# ------------
# ProteoWizard
# ------------

curl --location \
     --request POST \
     --form "downloadtype=bt17" \
     --output "pwiz-bin-linux-x86_64-gcc48-release-3_0_10800.tar.bz2" \
     "http://data.mallicklab.com/download.php"
mkdir /usr/local/proteowizard
tar jxvf pwiz-bin-linux-x86_64-gcc48-release-3_0_10800.tar.bz2 --directory=/usr/local/proteowizard
rm --force pwiz-bin-linux-x86_64-gcc48-release-3_0_10800.tar.bz2
cat > /etc/profile.d/proteowizard.sh << EOT
export PATH=/usr/local/proteowizard:\$PATH
EOT


# -----------
# bumbershoot
# -----------

curl --location \
     --request POST \
     --form "downloadtype=ProteoWizard_Bumbershoot_Linux_X86_64" \
     --output "bumbershoot-bin-linux-gcc48-release-3_0_10800.tar.bz2" \
     "http://data.mallicklab.com/download.php"
mkdir /usr/local/bumbershoot
tar jxvf bumbershoot-bin-linux-gcc48-release-3_0_10800.tar.bz2 --directory=/usr/local/bumbershoot
rm --force pwiz-bin-linux-x86_64-gcc48-release-3_0_10800.tar.bz2
cat > /etc/profile.d/bumbershoot.sh << EOT
export PATH=/usr/local/bumbershoot:\$PATH
EOT


# -------
# bftools
# -------

curl --location \
     --output bftools.zip \
     "http://downloads.openmicroscopy.org/latest/bio-formats5.4/artifacts/bftools.zip"
unzip -o -d /usr/local bftools.zip
rm --force bftools.zip
cat > /etc/profile.d/bftools.sh << EOT
export PATH=/usr/local/bftools:\$PATH
EOT


# ---------
# biopython
# ---------

pip install biopython


# ----
# RSEM
# ----

curl --location \
     --output RSEM-1.3.0.tar.gz \
     "https://github.com/deweylab/RSEM/archive/v1.3.0.tar.gz"
tar zxvf RSEM-1.3.0.tar.gz
cd RSEM-1.3.0
make
make ebseq
make install
cd ..
rm --recursive --force RSEM-1.3.0.tar.gz RSEM-1.3.0
yum -y remove gcc-c++


# --------------
# WINGS - Labkey
# --------------

git clone https://github.com/IKCAP/DISK.git

cd DISK

python setup.py install

cd ..
rm --recursive --force DISK
