# Poke-Dolar-Instagram 👾💰

O **Poke-Dolar-Instagram** automatiza a coleta da cotação do dólar e associa ao Pokémon correspondente na Pokedex, publicando o resultado diariamente no Instagram. 📈📸 Siga a página e acompanhe a variação do dólar de uma maneira diferente: [@pokedolar.diario](https://www.instagram.com/pokedolar.diario/)

📝 _A ideia do projeto foi inspirada no PokeDolar Bot para [Twitter/X](https://github.com/PokeDolar/pokedolar_bot)_

## 🛠️ Tecnologias Utilizadas

### ⚙️ Backend
- 🔥 **Quarkus** com **Java 23** e **GraalVM**, explorando a capacidade de gerar **binários nativos** para reduzir consumo de recursos na AWS Lambda
- ☁️ **AWS Lambda**, utilizando:
    - 🏗️ Execução de uma **imagem nativa** gerada com GraalVM, garantindo inicialização ultrarrápida e menor consumo de memória
    - 🐍 Um script em **Python** para geração dinâmica de imagens
    - 🔄 Um mecanismo de **refresh de token** para a API do Instagram
- 🐳 **Docker**, utilizado para empacotamento e execução de aplicações nativas

### ☁️ Infraestrutura e Cloud
- 📦 **AWS S3** para armazenamento de imagens dos pokémon
- 📡 **AWS SNS e SSM** para gerenciamento de eventos e segurança
- ⏰ **AWS EventBridge** para agendamento de execuções automatizadas
- ✨ **AWS Bedrock** é utilizado para gerar legendas dinâmicas e criativas para os posts com IA generativa, utilizando modelo Llama 3.3 70B Instruct da META

### 🔗 Integrações e APIs
- 👾 **PokeAPI**, utilizada para obter dados sobre os Pokémon
- 📲 **GraphAPI do Instagram**, utilizada para publicar as imagens de forma automatizada

## 🚀 Implantação e Funcionamento

O projeto é totalmente hospedado na AWS, onde os serviços são orquestrados para garantir a execução diária do processo. A aplicação principal roda como uma **AWS Lambda** compilada de forma nativa, garantindo alto desempenho. O fluxo de funcionamento ocorre da seguinte maneira:

1. ⏰ O **EventBridge** agenda a execução diária da Lambda principal.
2. 💰 A Lambda principal coleta a cotação do dólar e determina o Pokémon correspondente.
3. 🖼️ Um script em **Python**, executado por outra Lambda, gera uma imagem com as informações.
4. ✨ AWS Bedrock cria a legenda do post com IA generativa.
5. 📂 A imagem gerada é armazenada no **S3** e publicada automaticamente via **GraphAPI do Instagram**.
6. 🔄 O sistema mantém um mecanismo de **refresh de token**, garantindo a continuidade da autenticação na API do Instagram.

**👨‍💻 Autor:** Anderson Alex Durante (https://www.linkedin.com/in/andersonalexdurante/)
