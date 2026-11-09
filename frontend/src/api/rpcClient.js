// Cliente JSON-RPC 2.0 sobre WebSocket.
// chamar() faz uma chamada remota e devolve uma Promise com o resultado;
// registrar() expõe um método local que o servidor pode chamar.

export class ClienteRpc {
  #ws = null;
  #proximoId = 1;
  #pendentes = new Map();
  #metodos = new Map();

  aoDesconectar = () => {};

  get conectado() {
    return this.#ws?.readyState === WebSocket.OPEN;
  }

  registrar(metodo, funcao) {
    this.#metodos.set(metodo, funcao);
  }

  conectar(url) {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(url);
      let aberto = false;
      ws.onopen = () => {
        aberto = true;
        this.#ws = ws;
        resolve();
      };
      ws.onmessage = (evento) => this.#receber(JSON.parse(evento.data));
      ws.onclose = () => {
        if (!aberto) {
          reject(new Error('Não foi possível conectar ao servidor.'));
          return;
        }
        for (const { reject: rejeitar } of this.#pendentes.values()) {
          rejeitar(new Error('Conexão perdida.'));
        }
        this.#pendentes.clear();
        this.aoDesconectar();
      };
    });
  }

  chamar(metodo, params = {}) {
    if (!this.conectado) return Promise.reject(new Error('Sem conexão com o servidor.'));
    const id = this.#proximoId++;
    return new Promise((resolve, reject) => {
      this.#pendentes.set(id, { resolve, reject });
      this.#ws.send(JSON.stringify({ jsonrpc: '2.0', id, method: metodo, params }));
    });
  }

  #receber(msg) {
    if (msg.method) {
      this.#metodos.get(msg.method)?.(msg.params);
      return;
    }
    const pendente = this.#pendentes.get(msg.id);
    if (!pendente) return;
    this.#pendentes.delete(msg.id);
    if (msg.error) pendente.reject(new Error(msg.error.message));
    else pendente.resolve(msg.result);
  }
}
