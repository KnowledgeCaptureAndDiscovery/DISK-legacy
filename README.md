# DISK

## Installation

1. Clone the repository [https://github.com/IKCAP/DISK.git](https://github.com/IKCAP/DISK.git)

```bash
git clone https://github.com/IKCAP/DISK.git
```

2. Setup

    2.1 For Development

    ```bash
    python setup.py develop
    ```

    2.1 For Installation
    ```bash
    python setup.py install
    ```

## Configuring

Create a file .labkey-config.txt, with 4 entries
```bash
[default]

; URL of Labkey server; including the context path
base-url     = https://example.domain.com:8443/labkey

; Labkey credentials
username     = <username>

; Labkey credentials
password     = <password>

; Labkey project name
project-name = <project>
```

The tool loads configuration in following order

1. From .labkey-config.txt file in the user's home directory
2. .labkey-config.txt file in current directory.
3. File passed as through the command line's --config option.
4. Command line arguments for each option.


## MS2 Analysis Configuration

In .labkey-config.txt, add a section as follows
```bash
[ms2]

; Default search engine to use
search-engine     = <search-engine>

; Server directory where the input file should be uploaded
input-location    = <input-locaiton>

; Server directory where the FASTA file should be uploaded
fasta-location    = <fasta-file-locaiton>

; Server directory where the protocol file should be uploaded
; DO NOT CHANGE THIS
protocol-location = .labkey/protocols/%(search-engine)s/
```

